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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final PoolConfiguration poolConfiguration;
  private final RbsDao rbsDao;

  @Autowired
  public PoolService(PoolConfiguration poolConfiguration, RbsDao rbsDao) {
    this.poolConfiguration = poolConfiguration;
    this.rbsDao = rbsDao;
  }

  /** Initialize Pool from config and figure out pools to create/delete/update */
  public void initialize() {
    logger.info("Pool config update enabled: " + poolConfiguration.isUpdatePoolOnStart());
    if (poolConfiguration.isUpdatePoolOnStart()) {
      List<PoolWithResourceConfig> parsedPoolConfigs;
      parsedPoolConfigs = loadPoolConfig(poolConfiguration.getConfigPath());
      initializeFromConfig(parsedPoolConfigs);
    }
  }

  @VisibleForTesting
  void initializeFromConfig(List<PoolWithResourceConfig> parsedPoolConfigs) {
    Map<String, Pool> activatePoolMap =
        Maps.uniqueIndex(rbsDao.retrievePools(PoolStatus.ACTIVE), pool -> pool.id().toString());
    Map<String, PoolWithResourceConfig> parsedPoolConfigMap =
        Maps.uniqueIndex(parsedPoolConfigs, config -> config.poolConfig().getPoolId());

    Set<String> allPoolIds = Sets.union(activatePoolMap.keySet(), parsedPoolConfigMap.keySet());

    List<PoolWithResourceConfig> poolsToCreate = new ArrayList<>();
    List<Pool> poolsToDelete = new ArrayList<>();
    Map<PoolId, Integer> poolsToUpdateSize = new HashMap<>();
    for (String id : allPoolIds) {
      if (parsedPoolConfigMap.containsKey(id) && !activatePoolMap.containsKey(id)) {
        // Exists in config but not in DB.
        poolsToCreate.add(parsedPoolConfigMap.get(id));
      } else if (!parsedPoolConfigMap.containsKey(id) && activatePoolMap.containsKey(id)) {
        // Exists in DB but not in Config.
        poolsToDelete.add(activatePoolMap.get(id));
      } else {
        Pool dbPool = activatePoolMap.get(id);
        PoolWithResourceConfig configPool = parsedPoolConfigMap.get(id);
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
    deletePools(poolsToDelete);
    updatePoolSize(poolsToUpdateSize);
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

  private void deletePools(List<Pool> poolsToDelete) {
    // TODO: Delete pool
  }

  private void updatePoolSize(Map<PoolId, Integer> poolsToUpdateSize) {
    // TODO: Update pool size
  }
}
