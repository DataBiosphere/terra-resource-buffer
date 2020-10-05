package bio.terra.rbs.service.pool;

import static bio.terra.rbs.app.configuration.PoolConfiguration.POOL_SCHEMA_NAME;
import static bio.terra.rbs.app.configuration.PoolConfiguration.RESOURCE_CONFIG_SUB_DIR_NAME;

import bio.terra.rbs.app.configuration.PoolConfiguration;
import bio.terra.rbs.db.Pool;
import bio.terra.rbs.db.PoolId;
import bio.terra.rbs.db.PoolStatus;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.Pools;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final PoolConfiguration poolConfiguration;
  private final RbsDao rbsDao;
  private final ResourceConfigTypeVisitor typeVisitor;

  @Autowired
  public PoolService(
      PoolConfiguration poolConfiguration, RbsDao rbsDao, ResourceConfigTypeVisitor typeVisitor) {
    this.poolConfiguration = poolConfiguration;
    this.rbsDao = rbsDao;
    this.typeVisitor = typeVisitor;
  }

  /** Initialize Pool from config and figure out pools to create/delete/update */
  public void initialize() {
    logger.info("Pool config update enabled: " + poolConfiguration.isUpdatePoolOnStart());
    if (poolConfiguration.isUpdatePoolOnStart()) {
      initializeFromConfig();
    }
  }

  @VisibleForTesting
  void initializeFromConfig() {
    List<Pool> activatePools = rbsDao.retrievePools(PoolStatus.ACTIVE);
    Map<PoolConfig, ResourceConfig> parsedPoolConfigs =
        loadPoolConfig(poolConfiguration.getConfigPath());

    Map<String, Integer> activatePoolNameSizeMap =
        activatePools.stream().collect(Collectors.toMap(Pool::name, Pool::size));
    Map<String, Integer> parsedPoolNameSizeMap =
        parsedPoolConfigs.keySet().stream()
            .collect(Collectors.toMap(PoolConfig::getPoolName, PoolConfig::getSize));
    MapDifference<String, Integer> mapDifference =
        Maps.difference(activatePoolNameSizeMap, parsedPoolNameSizeMap);

    Map<String, Integer> poolsToCreate = mapDifference.entriesOnlyOnRight();
    Map<String, Integer> poolsToDelete = mapDifference.entriesOnlyOnLeft();
    Map<String, MapDifference.ValueDifference<Integer>> poolsToUpdateSize =
        mapDifference.entriesDiffering();

    // Create Pools
    createPools(poolsToCreate, parsedPoolConfigs);

    // TODO: Delete pool

    // TODO: Update pool size
  }

  private void createPools(
      Map<String, Integer> poolsToCreate, Map<PoolConfig, ResourceConfig> parsedPools) {
    List<Pool> pools = new ArrayList<>();
    for (Map.Entry<PoolConfig, ResourceConfig> poolConfig : parsedPools.entrySet()) {
      if (poolsToCreate.containsKey(poolConfig.getKey().getPoolName())) {
        Pool createdPool =
            Pool.builder()
                .id(PoolId.create(UUID.randomUUID()))
                .name(poolConfig.getKey().getPoolName())
                .size(poolConfig.getKey().getSize())
                .resourceConfig(poolConfig.getValue())
                .creation(Instant.now())
                .resourceType(typeVisitor.accept(poolConfig.getValue()))
                .status(PoolStatus.ACTIVE)
                .build();
        pools.add(createdPool);
      }
    }
    rbsDao.createPools(pools);
  }

  /** Parse and load {@link PoolConfig} and {@link ResourceConfig} from file. */
  @VisibleForTesting
  public static Map<PoolConfig, ResourceConfig> loadPoolConfig(String folderName) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    // Parse PoolConfig
    Pools pools;
    try {
      pools =
          objectMapper.readValue(
              classLoader.getResourceAsStream(folderName + "/" + POOL_SCHEMA_NAME), Pools.class);
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to parse pool schema for folder %s", folderName), e);
    }

    // Parse ResourceConfig
    Map<String, ResourceConfig> resourceConfigNameMap = new HashMap<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      Resource[] resources =
          resolver.getResources(folderName + "/" + RESOURCE_CONFIG_SUB_DIR_NAME + "/*.yml");
      for (Resource resource : resources) {
        ResourceConfig resourceConfig =
            objectMapper.readValue(resource.getInputStream(), ResourceConfig.class);
        resourceConfigNameMap.put(resourceConfig.getConfigName(), resourceConfig);
      }
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to parse ResourceConfig for %s", folderName), e);
    }

    // Verify PoolConfig and ResourceConfig name match, then construct parsed result.
    Map<PoolConfig, ResourceConfig> parsedPoolMap = new HashMap<>();
    for (PoolConfig poolConfig : pools.getPoolConfigs()) {
      if (!resourceConfigNameMap.containsKey(poolConfig.getResourceConfigName())) {
        throw new RuntimeException(
            String.format(
                "ResourceConfig not found for name: %s, folder: %s",
                poolConfig.getResourceConfigName(), folderName));
      }
      parsedPoolMap.put(poolConfig, resourceConfigNameMap.get(poolConfig.getResourceConfigName()));
    }
    return parsedPoolMap;
  }
}
