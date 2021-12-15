package bio.terra.buffer.service.pool;

import static bio.terra.buffer.service.pool.PoolConfigLoader.loadPoolConfig;
import static bio.terra.common.db.DatabaseRetryUtils.executeAndRetry;

import bio.terra.buffer.app.configuration.PoolConfiguration;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.PoolAndResourceStates;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.RequestHandoutId;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceConfigVisitor;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.exception.NotFoundException;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.PoolConfig;
import bio.terra.buffer.generated.model.PoolInfo;
import bio.terra.buffer.generated.model.ResourceInfo;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InternalServerErrorException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final PoolConfiguration poolConfiguration;
  private final BufferDao bufferDao;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  public PoolService(
      PoolConfiguration poolConfiguration,
      BufferDao bufferDao,
      TransactionTemplate transactionTemplate) {
    this.poolConfiguration = poolConfiguration;
    this.bufferDao = bufferDao;
    this.transactionTemplate = transactionTemplate;
  }

  /** Initialize Pool from config and figure out pools to create/deactivate/update */
  public void initialize() {
    logger.info("Pool config update enabled: " + poolConfiguration.isUpdatePoolOnStart());
    if (poolConfiguration.isUpdatePoolOnStart()) {
      List<PoolWithResourceConfig> parsedPoolConfigs;
      parsedPoolConfigs =
          loadPoolConfig(
              poolConfiguration.getConfigPath(), poolConfiguration.getReadConfigFromSystemFile());
      updateFromConfig(parsedPoolConfigs);
    }
  }

  /** Handout resource to client by given {@link PoolId} and {@link RequestHandoutId}. */
  public ResourceInfo handoutResource(PoolId poolId, RequestHandoutId requestHandoutId) {
    return createResourceInfo(
        transactionTemplate.execute(
            status -> handoutResourceTransactionally(poolId, requestHandoutId, status)),
        requestHandoutId);
  }

  /** Gets pool information by given {@link PoolId}. */
  public PoolInfo getPoolInfo(PoolId poolId) {
    Optional<PoolAndResourceStates> poolAndResourceStates =
        bufferDao.retrievePoolAndResourceStatesById(poolId);
    if (!poolAndResourceStates.isPresent()) {
      throw new NotFoundException(String.format("Pool %s not found", poolId));
    }
    Pool pool = poolAndResourceStates.get().pool();
    Multiset<ResourceState> resourceStates = poolAndResourceStates.get().resourceStates();
    return new PoolInfo()
        .poolConfig(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(pool.size())
                .resourceConfigName(pool.resourceConfig().getConfigName()))
        .status(bio.terra.buffer.generated.model.PoolStatus.valueOf(pool.status().toString()))
        .putResourceStateCountItem(
            ResourceState.CREATING.name(), resourceStates.count(ResourceState.CREATING))
        .putResourceStateCountItem(
            ResourceState.READY.name(), resourceStates.count(ResourceState.READY))
        .putResourceStateCountItem(
            ResourceState.DELETED.name(), resourceStates.count(ResourceState.DELETED))
        .putResourceStateCountItem(
            ResourceState.HANDED_OUT.name(), resourceStates.count(ResourceState.HANDED_OUT));
  }

  /** Process handout resource in on transcation(anything failure will cause database rollback). */
  private Resource handoutResourceTransactionally(
      PoolId poolId, RequestHandoutId requestHandoutId, TransactionStatus unused) {
    Optional<Pool> pool = bufferDao.retrievePool(poolId);
    if (!pool.isPresent() || !pool.get().status().equals(PoolStatus.ACTIVE)) {
      throw new BadRequestException(String.format("Invalid pool id: %s.", poolId));
    }
    try {
      // Retry 20 times of 2 seconds each.
      Optional<Resource> resource =
          executeAndRetry(
              () -> bufferDao.updateOneReadyResourceToHandedOut(poolId, requestHandoutId),
              Duration.ofSeconds(2),
              20);
      if (resource.isPresent()) {
        return resource.get();
      } else {
        throw new NotFoundException(
            String.format(
                "No resource is ready to use at this moment for pool: %s. Please try later",
                poolId));
      }
    } catch (InterruptedException | DataAccessException e) {
      throw new InternalServerErrorException(
          String.format(
              "Failed to update one resource state from READY to HANDED_OUT for pool {}", poolId));
    }
  }

  @VisibleForTesting
  public void updateFromConfig(List<PoolWithResourceConfig> parsedPoolConfigs) {
    transactionTemplate.execute(
        status -> {
          Map<PoolId, Pool> allDbPoolsMap =
              Maps.uniqueIndex(bufferDao.retrievePools(), pool -> pool.id());
          Map<PoolId, PoolWithResourceConfig> parsedPoolConfigMap =
              Maps.uniqueIndex(
                  parsedPoolConfigs, config -> PoolId.create(config.poolConfig().getPoolId()));

          Set<PoolId> allPoolIds = Sets.union(allDbPoolsMap.keySet(), parsedPoolConfigMap.keySet());

          List<PoolWithResourceConfig> poolsToCreate = new ArrayList<>();
          List<Pool> poolsToDeactivate = new ArrayList<>();
          Map<PoolId, Integer> poolsToUpdateSize = new HashMap<>();

          // Compare pool ids in DB and config. Validate config change is valid then update DB based
          // on
          // the change.
          for (PoolId id : allPoolIds) {
            if (parsedPoolConfigMap.containsKey(id) && !allDbPoolsMap.containsKey(id)) {
              // Exists in config but not in DB.
              poolsToCreate.add(parsedPoolConfigMap.get(id));
            } else if (!parsedPoolConfigMap.containsKey(id) && allDbPoolsMap.containsKey(id)) {
              // Exists in DB but not in Config.
              poolsToDeactivate.add(allDbPoolsMap.get(id));
            } else {
              Pool dbPool = allDbPoolsMap.get(id);
              PoolWithResourceConfig configPool = parsedPoolConfigMap.get(id);
              if (dbPool.status().equals(PoolStatus.DEACTIVATED)) {
                throw new RuntimeException(
                    String.format(
                        "An existing deactivated pool with duplicate id(id= %s) found, "
                            + "please consider change the pool id.",
                        id));
              }
              if (!dbPool.resourceConfig().equals(configPool.resourceConfig())) {
                // Updating existing resource config other than size.
                // Note that we already verify pool configs' resource_config_name matches inside
                // ResourceConfig name when loading from file.
                throw new RuntimeException(
                    String.format(
                        "Updating ResourceConfig on existing pool(id= %s) is not allowed, "
                            + "please create a new pool config instead",
                        id));
              } else if (dbPool.size() != (configPool.poolConfig().getSize())) {
                // Exists in both places but need to update size.
                poolsToUpdateSize.put(dbPool.id(), configPool.poolConfig().getSize());
              }
            }
          }
          createPools(poolsToCreate);
          deactivatePools(poolsToDeactivate);
          updatePoolSize(poolsToUpdateSize);
          return true;
        });
  }

  private void createPools(List<PoolWithResourceConfig> poolsToCreate) {
    List<Pool> pools = new ArrayList<>();
    for (PoolWithResourceConfig poolConfig : poolsToCreate) {
      Pool createdPool =
          Pool.builder()
              .id(PoolId.create(poolConfig.poolConfig().getPoolId()))
              .size(poolConfig.poolConfig().getSize())
              .resourceConfig(poolConfig.resourceConfig())
              .creation(Instant.now())
              .resourceType(
                  ResourceConfigVisitor.visit(
                          poolConfig.resourceConfig(), new ResourceConfigTypeVisitor())
                      .orElseThrow(
                          () ->
                              new RuntimeException(
                                  String.format(
                                      "Unknown ResourceType for PoolConfig %s", poolConfig))))
              .status(PoolStatus.ACTIVE)
              .build();
      pools.add(createdPool);
    }
    bufferDao.createPools(pools);
  }

  private void deactivatePools(List<Pool> poolsToDeactivate) {
    bufferDao.deactivatePools(
        poolsToDeactivate.stream().map(Pool::id).collect(Collectors.toList()));
  }

  private void updatePoolSize(Map<PoolId, Integer> poolSizes) {
    bufferDao.updatePoolsSize(poolSizes);
  }

  /** Creates {@link ResourceInfo} from given {@link Resource}. */
  private static ResourceInfo createResourceInfo(
      Resource resource, RequestHandoutId requestHandoutId) {
    if (resource.cloudResourceUid() == null) {
      // Should never happen.
      throw new InternalServerErrorException(
          String.format("Invalid resource. Id: %s", resource.id()));
    }
    return new ResourceInfo()
        .poolId(resource.poolId().id())
        .cloudResourceUid(resource.cloudResourceUid())
        .requestHandoutId(requestHandoutId.id());
  }
}
