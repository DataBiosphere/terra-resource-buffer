package bio.terra.rbs.service.pool;

import bio.terra.rbs.app.configuration.PoolConfiguration;
import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.db.Pool;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.Pools;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static bio.terra.rbs.app.configuration.PoolConfiguration.POOL_SCHEMA_NAME;
import static bio.terra.rbs.app.configuration.PoolConfiguration.RESOURCE_CONFIG_SUB_DIR_NAME;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final ObjectMapper objectMapper;
  private final PoolConfiguration poolConfiguration;
  private final RbsDao rbsDao;

  @Autowired
  public PoolService(ObjectMapper objectMapper, PoolConfiguration poolConfiguration, RbsDao rbsDao) {
    this.objectMapper = objectMapper;
    this.poolConfiguration = poolConfiguration;
    this.rbsDao = rbsDao;
  }

  public void initialize() {
    List<Pool> activePools = rbsDao.retrieveActivePools();

  }

  private void loadPoolConfigs(File configFolder) {
      ClassLoader classLoader = this.getClass().getClassLoader();

    Pools pools =
            null;
    try {
      pools = objectMapper.readValue(
              new ClassPathResource(folderName + POOL_SCHEMA_NAME).getFile(), Pools.class);
    } catch (IOException e) {
      throw new InvalidPoolConfigException(String.format("Failed to parse PoolConfig for %s", folderName + POOL_SCHEMA_NAME));
    }

    File configFolder =
              new File(
                      classLoader.getResource(folderName + "/" + RESOURCE_CONFIG_SUB_DIR_NAME).getFile());
      Set<String> resourceConfigName = new HashSet<>();
      List<ResourceConfig> resourceConfigs = new ArrayList<>();

      for (File file : configFolder.listFiles()) {
        ResourceConfig resourceConfig = null;
        try {
          resourceConfig = objectMapper.readValue(file, ResourceConfig.class);
        } catch (IOException e) {
          throw new InvalidPoolConfigException(String.format("Failed to parse PoolConfig for %s", folderName + POOL_SCHEMA_NAME));
        }
        if (resourceConfigName.contains(resourceConfig.getConfigName())) {
          throw new RuntimeException(String.format(
                  "Duplicate config name found for ResourceConfig: %s, folder: %s",
                  resourceConfig.getConfigName(), folderName));
        }
        resourceConfigName.add(resourceConfig.getConfigName());
        resourceConfigs.add(resourceConfig);
      }

      for (PoolConfig poolConfig : pools.getPoolConfigs()) {

        if (!resourceConfigName.contains(poolConfig.getResourceConfigName())) {
          throw new RuntimeException( String.format(
                  "ResourceConfig not found for name: %s, folder: %s",
                  poolConfig.getResourceConfigName(), folderName));
        }
      }
    }
}
