package bio.terra.buffer.config;

import static bio.terra.buffer.service.pool.PoolConfigLoader.loadPoolConfig;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableList;
import java.util.*;
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
  /** List of pool config folders for all environments, e.g. prod, staging, dev. */
  private static final List<String> POOL_CONFIG_FOLDERS =
      ImmutableList.of(
          "config/alpha/",
          "config/buffertest/",
          "config/dev/",
          "config/prod/",
          "config/perf/",
          "config/staging/",
          "config/tools/",
          "config/toolsalpha/");

  @Test
  public void testConfigValid() {
    for (String folder : POOL_CONFIG_FOLDERS) {
      assertPoolConfigValid(folder, false);
    }
  }

  @Test
  public void testConfigValid_readFromSystemFile() {
    for (String folder : POOL_CONFIG_FOLDERS) {
      assertPoolConfigValid(folder, true);
    }
  }

  private void assertPoolConfigValid(String folderName, boolean readFromSystemFile) {
    try {
      loadPoolConfig(folderName, readFromSystemFile);
    } catch (Exception e) {
      fail(String.format("Validate %s resource failed with exception %s", folderName, e));
      throw e;
    }
  }
}
