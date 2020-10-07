package bio.terra.rbs.service.pool;

import static bio.terra.rbs.service.pool.PoolConfigLoader.loadPoolConfig;

import bio.terra.rbs.app.configuration.PoolConfiguration;
import bio.terra.rbs.common.ResourceConfigVisitor;
import bio.terra.rbs.db.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
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
      transactionTemplate.execute(status -> updateFromConfig(parsedPoolConfigs, status));
    }
  }

  @VisibleForTesting
  boolean updateFromConfig(
      List<PoolWithResourceConfig> parsedPoolConfigs, TransactionStatus status) {
    Map<PoolId, Pool> allDbPoolsMap = Maps.uniqueIndex(rbsDao.retrievePools(), pool -> pool.id());
    Map<PoolId, PoolWithResourceConfig> parsedPoolConfigMap =
        Maps.uniqueIndex(
            parsedPoolConfigs, config -> PoolId.create(config.poolConfig().getPoolId()));

    Set<PoolId> allPoolIds = Sets.union(allDbPoolsMap.keySet(), parsedPoolConfigMap.keySet());

    List<PoolWithResourceConfig> poolsToCreate = new ArrayList<>();
    List<Pool> poolsToDeactivate = new ArrayList<>();
    Map<PoolId, Integer> poolsToUpdateSize = new HashMap<>();

    // Compare pool ids in DB and config. Validate config change is valid then update DB based on
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
}
