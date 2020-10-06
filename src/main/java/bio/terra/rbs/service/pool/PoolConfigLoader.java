package bio.terra.rbs.service.pool;

import static bio.terra.rbs.app.configuration.PoolConfiguration.POOL_SCHEMA_NAME;
import static bio.terra.rbs.app.configuration.PoolConfiguration.RESOURCE_CONFIG_SUB_DIR_NAME;

import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.Pools;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Load and validate {@link PoolConfig} passed from config files.
 *
 * <p>Config is invalid if:
 *
 * <ul>
 *   <li>Duplicate pool ids in pool config.
 *   <li>Pools' corresponding resource config file.
 *   <li>Any file or json parsing error.
 * </ul>
 */
public class PoolConfigLoader {
  /** Parse and validate {@link PoolConfig} and {@link ResourceConfig} from file. */
  @VisibleForTesting
  public static List<PoolWithResourceConfig> loadPoolConfig(String folderName) {
    Pools pools = parsePools(folderName);
    Map<String, ResourceConfig> resourceConfigNameMap = parseResourceConfig(folderName);
    return combineParsedConfig(pools, resourceConfigNameMap);
  }

  /** Deserialize {@link Pools} which contains list of {@link PoolConfig} from config folder. */
  private static Pools parsePools(String folderName) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    try {
      return objectMapper.readValue(
          classLoader.getResourceAsStream(folderName + "/" + POOL_SCHEMA_NAME), Pools.class);
    } catch (IOException e) {
      throw new BadPoolConfigException(
          String.format("Failed to parse pool schema for folder %s", folderName), e);
    }
  }

  /** Deserialize {@link ResourceConfig} from config folder. */
  private static Map<String, ResourceConfig> parseResourceConfig(String folderName) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
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
      throw new BadPoolConfigException(
          String.format("Failed to parse ResourceConfig for %s", folderName), e);
    }
    return resourceConfigNameMap;
  }

  /**
   * Combine parsed {@link Pools} with {@link ResourceConfig} into {@link PoolWithResourceConfig}.
   */
  @VisibleForTesting
  static List<PoolWithResourceConfig> combineParsedConfig(
      Pools pools, Map<String, ResourceConfig> resourceConfigNameMap) {
    List<PoolWithResourceConfig> result = new ArrayList<>();
    Set<String> poolIds = new HashSet<>();
    for (PoolConfig poolConfig : pools.getPoolConfigs()) {
      // Verify pool id is unique in config.
      if (poolIds.contains(poolConfig.getPoolId())) {
        throw new BadPoolConfigException(
            String.format("Duplicate PoolId found for: %s", poolConfig.getPoolId()));
      }
      poolIds.add(poolConfig.getPoolId());

      // Verify resource configs exist.
      if (!resourceConfigNameMap.containsKey(poolConfig.getResourceConfigName())) {
        throw new BadPoolConfigException(
            String.format(
                "ResourceConfig not found for name: %s", poolConfig.getResourceConfigName()));
      }
      result.add(
          PoolWithResourceConfig.create(
              poolConfig, resourceConfigNameMap.get(poolConfig.getResourceConfigName())));
    }
    return result;
  }
}
