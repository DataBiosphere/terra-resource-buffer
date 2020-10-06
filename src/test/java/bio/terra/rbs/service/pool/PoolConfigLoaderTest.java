package bio.terra.rbs.service.pool;

import static bio.terra.rbs.service.pool.PoolConfigLoader.combineParsedConfig;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.Pools;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

public class PoolConfigLoaderTest extends BaseUnitTest {
  @Test
  public void combineParsedResult_duplicatePoolId() {
    String resourceConfigName = "configName";
    PoolConfig poolConfig = new PoolConfig().poolId("id").resourceConfigName(resourceConfigName);
    Pools pools = new Pools().poolConfigs(ImmutableList.of(poolConfig, poolConfig));
    ResourceConfig resourceConfig = new ResourceConfig().configName(resourceConfigName);

    assertThrows(
        BadPoolConfigException.class,
        () -> combineParsedConfig(pools, ImmutableMap.of(resourceConfigName, resourceConfig)));
  }

  @Test
  public void combineParsedResult_resourceConfigNotFound() {
    String resourceConfigName = "configName";
    PoolConfig poolConfig = new PoolConfig().poolId("id").resourceConfigName("badName");
    Pools pools = new Pools().poolConfigs(ImmutableList.of(poolConfig, poolConfig));
    ResourceConfig resourceConfig = new ResourceConfig().configName(resourceConfigName);

    assertThrows(
        BadPoolConfigException.class,
        () -> combineParsedConfig(pools, ImmutableMap.of(resourceConfigName, resourceConfig)));
  }
}
