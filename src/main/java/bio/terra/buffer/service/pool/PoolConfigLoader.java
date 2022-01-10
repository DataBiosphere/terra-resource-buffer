package bio.terra.buffer.service.pool;

import bio.terra.buffer.generated.model.PoolConfig;
import bio.terra.buffer.generated.model.PoolConfigs;
import bio.terra.buffer.generated.model.ResourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
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
  public static List<PoolWithResourceConfig> loadPoolConfig(
      String folderName, Optional<String> systemFilePath) {
    PoolConfigs poolConfigs;
    Map<String, ResourceConfig> resourceConfigNameMap;
    if (systemFilePath.isPresent()) {
      poolConfigs = parsePoolsAsSystemFile(systemFilePath.get());
      resourceConfigNameMap = parseResourceConfigAsSystemFile(systemFilePath.get());
    } else {
      // TODO (PF-1273): clean up once all environments are switched to using system file path.
      poolConfigs = parsePools(folderName);
      resourceConfigNameMap = parseResourceConfig(folderName);
    }
    validateResourceConfig(new ArrayList<>(resourceConfigNameMap.values()));
    return combineParsedConfig(poolConfigs, resourceConfigNameMap);
  }

  /**
   * Deserializes {@link PoolConfigs} which contains map of {@link PoolConfig} keyed on pool id from
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

  /**
   * Deserializes {@link PoolConfigs} of the given {@code POOL_SCHEMA_NAME} file in the given config
   * folder.
   *
   * @param systemFilePath full path to the pool_schema.yaml file.
   */
  private static PoolConfigs parsePoolsAsSystemFile(String systemFilePath) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    File poolConfigFile = new File(systemFilePath + "/" + POOL_SCHEMA_NAME);
    try {
      return objectMapper.readValue(poolConfigFile.getCanonicalFile(), PoolConfigs.class);
    } catch (IOException e) {
      throw new BadPoolConfigException(
          String.format(
              "Failed to parse pool schema for %s", systemFilePath + "/" + POOL_SCHEMA_NAME),
          e);
    }
  }

  /**
   * Deserializes {@link ResourceConfig} of the all the config files in the given config folders.
   *
   * @param systemFilePath full path to the resource configs directory.
   */
  private static Map<String, ResourceConfig> parseResourceConfigAsSystemFile(
      String systemFilePath) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    Map<String, ResourceConfig> resourceConfigNameMap = new HashMap<>();
    try (Stream<Path> walk =
        Files.walk(
            Paths.get(systemFilePath + "/" + RESOURCE_CONFIG_SUB_DIR_NAME),
            1,
            FileVisitOption.FOLLOW_LINKS)) {
      walk.filter(path -> !Files.isDirectory(path))
          .forEach(
              path -> {
                try {
                  ResourceConfig config =
                      objectMapper.readValue(
                          path.toFile().getCanonicalFile(), ResourceConfig.class);
                  resourceConfigNameMap.put(config.getConfigName(), config);
                } catch (IOException e) {
                  throw new BadPoolConfigException(
                      String.format("Failed to parse ResourceConfig for %s", path), e);
                }
              });
    } catch (IOException e) {
      throw new BadPoolConfigException(
          String.format("Failed to walk the files in the directory %s", systemFilePath), e);
    }
    return resourceConfigNameMap;
  }

  /**
   * Deserializes {@link ResourceConfig} which contains map of {@link ResourceConfig} keyed on
   * resource config name from config folder.
   */
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

  /** Validates {@link ResourceConfig}. */
  private static void validateResourceConfig(List<ResourceConfig> resourceConfigs) {
    for (ResourceConfig config : resourceConfigs) {
      ResourceConfigValidator validator = ResourceConfigValidatorFactory.getValidator(config);
      validator.validate(config);
    }
  }
}
