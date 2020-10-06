package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.PoolConfigs;
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
 * <p>Pool configs are stored in config folder, the expected files under the folder are: {@code
 * resource-config - resource_config_1.yml - resource_config_2.yml pool_schema.yml }
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
  /** Pool schema file name should be the same name for all environments. */
  private static final String POOL_SCHEMA_NAME = "pool_schema.yml";
  /** Resource configs folder name should be the same name for all environments. */
  private static final String RESOURCE_CONFIG_SUB_DIR_NAME = "resource-config";

  /** Parse and validate {@link PoolConfig} and {@link ResourceConfig} from file. */
  @VisibleForTesting
  public static List<PoolWithResourceConfig> loadPoolConfig(String folderName) {
    PoolConfigs poolConfigs = parsePools(folderName);
    Map<String, ResourceConfig> resourceConfigNameMap = parseResourceConfig(folderName);
    return combineParsedConfig(poolConfigs, resourceConfigNameMap);
  }

  /**
   * Deserialize {@link PoolConfigs} which contains map of {@link PoolConfig} keyed on pool id from
   * config folder.
   */
  private static PoolConfigs parsePools(String folderName) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    try {
      return objectMapper.readValue(
          classLoader.getResourceAsStream(folderName + "/" + POOL_SCHEMA_NAME), PoolConfigs.class);
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
   * Combine parsed {@link PoolConfigs} with {@link ResourceConfig} into {@link
   * PoolWithResourceConfig}.
   */
  @VisibleForTesting
  static List<PoolWithResourceConfig> combineParsedConfig(
      PoolConfigs PoolConfigs, Map<String, ResourceConfig> resourceConfigNameMap) {
    List<PoolWithResourceConfig> result = new ArrayList<>();
    Set<String> seenPoolIds = new HashSet<>();
    for (PoolConfig poolConfig : PoolConfigs.getPoolConfigs()) {
      // Verify pool id is unique in config.
      if (!seenPoolIds.add(poolConfig.getPoolId())) {
        throw new BadPoolConfigException(
            String.format("Duplicate PoolId found for: %s", poolConfig.getPoolId()));
      }

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
