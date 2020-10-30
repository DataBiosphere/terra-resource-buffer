package bio.terra.rbs.service.pool;

import static bio.terra.rbs.service.pool.PoolConfigLoader.loadPoolConfig;

import bio.terra.rbs.app.configuration.PoolConfiguration;
import bio.terra.rbs.common.*;
import bio.terra.rbs.common.exception.BadRequestException;
import bio.terra.rbs.common.exception.InternalServerErrorException;
import bio.terra.rbs.common.exception.NotFoundException;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.PoolInfo;
import bio.terra.rbs.generated.model.ResourceInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final PoolConfiguration poolConfiguration;
  private final RbsDao rbsDao;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  public PoolService(
      PoolConfiguration poolConfiguration, RbsDao rbsDao, TransactionTemplate transactionTemplate) {
    this.poolConfiguration = poolConfiguration;
    this.rbsDao = rbsDao;
    this.transactionTemplate = transactionTemplate;
  }

  /** Initialize Pool from config and figure out pools to create/deactivate/update */
  public void initialize() {
    logger.info("Pool config update enabled: " + poolConfiguration.isUpdatePoolOnStart());
    if (poolConfiguration.isUpdatePoolOnStart()) {
      List<PoolWithResourceConfig> parsedPoolConfigs;
      parsedPoolConfigs = loadPoolConfig(poolConfiguration.getConfigPath());
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
        rbsDao.retrievePoolAndResourceStatesById(poolId);
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
        .status(bio.terra.rbs.generated.model.PoolStatus.valueOf(pool.status().toString()))
        .putResourceStateCountItem(
            ResourceState.CREATING.name(), resourceStates.count(ResourceState.CREATING))
        .putResourceStateCountItem(
            ResourceState.READY.name(), resourceStates.count(ResourceState.READY))
        .putResourceStateCountItem(
            ResourceState.DELETED.name(), resourceStates.count(ResourceState.DELETED))
        .putResourceStateCountItem(
            ResourceState.HANDED_OUT.name(), resourceStates.count(ResourceState.HANDED_OUT));
  }

  private Resource handoutResourceTransactionally(
      PoolId poolId, RequestHandoutId requestHandoutId, TransactionStatus unused) {
    Optional<Resource> existingResource = rbsDao.retrieveResource(poolId, requestHandoutId);
    Optional<Pool> pool = rbsDao.retrievePool(poolId);
    if (!pool.isPresent() || !pool.get().status().equals(PoolStatus.ACTIVE)) {
      throw new BadRequestException(String.format("Invalid pool id: %s.", poolId));
    }
    if (existingResource.isPresent()) {
      if (existingResource.get().state().equals(ResourceState.HANDED_OUT)) {
        return existingResource.get();
      } else {
        // Should never happens.
        throw new InternalServerErrorException(
            String.format(
                "Unexpected handed out resource state found in pool: id: %s, requestHandoutId: %s",
                poolId, requestHandoutId));
      }
    } else {
      List<Resource> resources = rbsDao.retrieveResources(poolId, ResourceState.READY, 1);
      if (resources.size() == 0) {
        throw new NotFoundException(
            String.format(
                "No resource is ready to use at this moment for pool: %s. Please try later",
                poolId));
      } else {
        Resource selectedResource = resources.get(0);
        if (!rbsDao.updateResourceAsHandedOut(selectedResource.id(), requestHandoutId)) {
          throw new InternalServerErrorException(
              "Error occurs when updating resource to handed out.");
        }
        ;
        return selectedResource;
      }
    }
  }

  @VisibleForTesting
  public void updateFromConfig(List<PoolWithResourceConfig> parsedPoolConfigs) {
    transactionTemplate.execute(
        status -> {
          Map<PoolId, Pool> allDbPoolsMap =
              Maps.uniqueIndex(rbsDao.retrievePools(), pool -> pool.id());
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
    rbsDao.createPools(pools);
  }

  private void deactivatePools(List<Pool> poolsToDeactivate) {
    rbsDao.deactivatePools(poolsToDeactivate.stream().map(Pool::id).collect(Collectors.toList()));
  }

  private void updatePoolSize(Map<PoolId, Integer> poolSizes) {
    rbsDao.updatePoolsSize(poolSizes);
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
