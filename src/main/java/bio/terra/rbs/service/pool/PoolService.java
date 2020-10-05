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
      ObjectMapper objectMapper,
      PoolConfiguration poolConfiguration,
      RbsDao rbsDao,
      ResourceConfigTypeVisitor typeVisitor) {
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
    System.out.println("~~~~~~~~!!!!!!!");
    Map<String, Pool> activePoolMap =
        rbsDao.retrievePools(PoolStatus.ACTIVE).stream()
            .collect(Collectors.toMap(Pool::name, pool -> pool));
    Map<String, Pool> loadedPoolMap = loadPoolConfig(poolConfiguration.getConfigPath());
    System.out.println(activePoolMap.size());
    MapDifference<String, Pool> mapDifference = Maps.difference(activePoolMap, loadedPoolMap);
    Map<String, Pool> poolsToCreate = mapDifference.entriesOnlyOnRight();
    createPools(poolsToCreate);

    // TODO: Delete pool
    Map<String, Pool> poolsToDelete = mapDifference.entriesOnlyOnLeft();

    // TODO: Update pool size
  }

  private void createPools(Map<String, Pool> poolsToCreate) {
    List<Pool> pools = new ArrayList<>();
    for (Pool pool : poolsToCreate.values()) {
      Pool createdPool =
          pool.toBuilder()
              .id(PoolId.create(UUID.randomUUID()))
              .creation(Instant.now())
              .resourceType(typeVisitor.accept(pool.resourceConfig()))
              .status(PoolStatus.ACTIVE)
              .build();
      pools.add(createdPool);
    }
    rbsDao.createPools(pools);
  }

  /** Parse and load {@link PoolConfig} and {@link ResourceConfig} from file. */
  @VisibleForTesting
  public static Map<String, Pool> loadPoolConfig(String folderName) {
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
    Map<String, Pool> parsedPoolMap = new HashMap<>();
    for (PoolConfig poolConfig : pools.getPoolConfigs()) {
      if (!resourceConfigNameMap.containsKey(poolConfig.getResourceConfigName())) {
        throw new RuntimeException(
            String.format(
                "ResourceConfig not found for name: %s, folder: %s",
                poolConfig.getResourceConfigName(), folderName));
      }
      Pool pool =
          Pool.builder()
              .name(poolConfig.getPoolName())
              .size(poolConfig.getSize())
              .resourceConfig(resourceConfigNameMap.get(poolConfig.getResourceConfigName()))
              .build();
      parsedPoolMap.put(pool.name(), pool);
    }
    return parsedPoolMap;
  }
}
