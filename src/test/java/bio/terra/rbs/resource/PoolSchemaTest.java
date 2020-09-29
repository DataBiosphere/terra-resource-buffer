package bio.terra.rbs.resource;

import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.Pools;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Validates PoolConfig for all pool config folders to verify
 *
 * <ul>
 *   <li>Deserialize Pool Config
 *   <li>Deserialize Resource Config
 *   <li>Resource name with version exists in pool configs.
 * </ul>
 */
@Tag("unit")
public class PoolSchemaTest {
  /** PList of pool config folders for all environments, e.g. prod, staging, dev. */
  private static final List<String> POOL_CONFIG_FOLDERS =
      ImmutableList.of("src/main/resources/config/pool-config/dev/");
  /** Pool schema file name should be the same name for all environments. */
  private static final String POOL_SCHEMA_NAME = "pool_schema.yml";
  /** Resource configs folder name should be the same name for all environments. */
  private static final String RESOURCE_CONFIG_SUB_DIR_NAME = "resource-config";

  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

  @Test
  public void testConfigValid() throws Exception {
    for (String folder : POOL_CONFIG_FOLDERS) {
      assertPoolConfigValid(folder);
    }
  }

  private void assertPoolConfigValid(String folderName) throws Exception {
    try {
      Pools pools = mapper.readValue(new File(folderName + POOL_SCHEMA_NAME), Pools.class);
      File configFolder = new File(folderName + "/" + RESOURCE_CONFIG_SUB_DIR_NAME);
      Map<String, String> resourceNameVersionMap = new HashMap<>();
      for (File file : configFolder.listFiles()) {
        ResourceConfig resourceConfig = mapper.readValue(file, ResourceConfig.class);
        if (resourceNameVersionMap.containsKey(resourceConfig.getConfigName())) {
          fail(
              String.format(
                  "Duplicate key found for ResourceConfig: %s, folder:",
                  resourceConfig.getConfigName(), folderName));
        }
        resourceNameVersionMap.put(resourceConfig.getConfigName(), resourceConfig.getVersion());
      }

      for (PoolConfig poolConfig : pools.getPoolConfigs()) {
        if (!resourceNameVersionMap.containsKey(poolConfig.getResourceConfigName())
            || !resourceNameVersionMap
                .get(poolConfig.getResourceConfigName())
                .equals(poolConfig.getVersion())) {
          fail(
              String.format(
                  "ResourceConfig not found for name: %s, version:%s, folder: %s",
                  poolConfig.getResourceConfigName(), poolConfig.getVersion(), folderName));
        }
      }
    } catch (Exception e) {
      fail(String.format("Validate %s resource failed with exception %s", folderName, e));
      throw e;
    }
  }
}
