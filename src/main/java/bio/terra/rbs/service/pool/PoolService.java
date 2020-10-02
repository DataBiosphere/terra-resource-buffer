package bio.terra.rbs.service.pool;

import static bio.terra.rbs.app.configuration.PoolConfiguration.POOL_SCHEMA_NAME;
import static bio.terra.rbs.app.configuration.PoolConfiguration.RESOURCE_CONFIG_SUB_DIR_NAME;

import bio.terra.rbs.app.configuration.PoolConfiguration;
import bio.terra.rbs.common.exception.InvalidPoolConfigException;
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
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final ObjectMapper objectMapper;
  private final PoolConfiguration poolConfiguration;
  private final RbsDao rbsDao;
  private final ResourceConfigTypeVisitor typeVisitor;

  @Autowired
  public PoolService(
      ObjectMapper objectMapper,
      PoolConfiguration poolConfiguration,
      RbsDao rbsDao,
      ResourceConfigTypeVisitor typeVisitor) {
    this.objectMapper = objectMapper;
    this.poolConfiguration = poolConfiguration;
    this.rbsDao = rbsDao;
    this.typeVisitor = typeVisitor;
  }

  /** Initialize Pool*/
  public void initialize() {
    logger.info("Pool config update enabled: " + poolConfiguration.isUpdatePoolOnStart());
    if(!poolConfiguration.isUpdatePoolOnStart()) {
      return;
    }
    Map<String, Pool> activePoolMap =
        rbsDao.retrievePools(PoolStatus.ACTIVE).stream()
            .collect(Collectors.toMap(Pool::name, pool -> pool));
    Map<String, Pool> loadedPoolMap = loadPoolConfig(poolConfiguration.getConfigPath());

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
      pool.toBuilder()
          .id(PoolId.create(UUID.randomUUID()))
          .creation(Instant.now())
          .resourceType(typeVisitor.accept(pool.resourceConfig()))
          .status(PoolStatus.ACTIVE)
          .build();
      pools.add(pool);
    }
    rbsDao.createPools(pools);
  }

  /** Parse and load {@link PoolConfig} and {@link ResourceConfig} from file. */
  @VisibleForTesting
  public static Map<String, Pool> loadPoolConfig(String folderName) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    // Parse PoolConfig
    Pools pools;
    try {
      pools =
          objectMapper.readValue(
              new ClassPathResource(folderName + POOL_SCHEMA_NAME).getFile(), Pools.class);
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Failed to parse PoolConfig for %s", folderName + POOL_SCHEMA_NAME));
    }

    // Parse ResourceConfig
    File configFolder;
    try {
      configFolder =
          new ClassPathResource(folderName + "/" + RESOURCE_CONFIG_SUB_DIR_NAME).getFile();
    } catch (IOException e) {
      throw new InvalidPoolConfigException(
          String.format("Failed to load resource configs from %s", folderName));
    }

    Map<String, ResourceConfig> resourceConfigNameMap = new HashMap<>();
    for (File file : configFolder.listFiles()) {
      ResourceConfig resourceConfig = null;
      try {
        resourceConfig = objectMapper.readValue(file, ResourceConfig.class);
        resourceConfigNameMap.put(resourceConfig.getConfigName(), resourceConfig);
      } catch (IOException e) {
        throw new RuntimeException(
            String.format("Failed to parse PoolConfig for %s", file.getName()));
      }
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
