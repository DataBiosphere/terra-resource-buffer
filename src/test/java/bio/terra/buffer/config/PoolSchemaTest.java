package bio.terra.buffer.config;

import static bio.terra.buffer.service.pool.PoolConfigLoader.loadPoolConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableList;
import java.util.*;
import javax.annotation.Nullable;
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
  private static final String CONFIG_FOLDER = "config/";

  /** List of pool config folders for all environments, e.g. prod, staging, dev. */
  private static final List<String> POOL_CONFIG_FOLDERS =
      ImmutableList.of(
          "alpha/", "buffertest/", "dev/", "prod/", "perf/", "staging/", "tools/", "toolsalpha/");

  @Test
  public void testConfigValid() {
    for (String folder : POOL_CONFIG_FOLDERS) {
      assertPoolConfigValid(CONFIG_FOLDER + folder, null);
    }
  }

  @Test
  public void testConfigValid_readFromSystemFile() {
    for (String folder : POOL_CONFIG_FOLDERS) {
      assertPoolConfigValid(
          CONFIG_FOLDER + folder, "src/main/resources" + "/" + CONFIG_FOLDER + folder);
    }
  }

  @Test
  public void loadPoolConfig_systemFilePathIsSymbolicLink_configValid() {
    assertEquals(
        2, loadPoolConfig("config", Optional.of("./src/test/java/bio/terra/buffer/config")).size());
  }

  private void assertPoolConfigValid(String folderName, @Nullable String systemFilePath) {
    try {
      loadPoolConfig(folderName, Optional.ofNullable(systemFilePath));
    } catch (Exception e) {
      fail(String.format("Validate %s resource failed with exception %s", folderName, e));
      throw e;
    }
  }
}
