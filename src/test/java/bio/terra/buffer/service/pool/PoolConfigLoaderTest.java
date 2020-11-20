package bio.terra.buffer.service.pool;

import static bio.terra.buffer.service.pool.PoolConfigLoader.combineParsedConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.generated.model.PoolConfig;
import bio.terra.buffer.generated.model.PoolConfigs;
import bio.terra.buffer.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class PoolConfigLoaderTest extends BaseUnitTest {
  @Test
  public void combineParsedResult() {
    String resourceConfigName = "configName";
    PoolConfig poolConfig = new PoolConfig().poolId("id").resourceConfigName(resourceConfigName);
    PoolConfigs pools = new PoolConfigs().poolConfigs(ImmutableList.of(poolConfig));
    ResourceConfig resourceConfig = new ResourceConfig().configName(resourceConfigName);

    PoolWithResourceConfig expected = PoolWithResourceConfig.create(poolConfig, resourceConfig);

    assertThat(
        combineParsedConfig(pools, ImmutableMap.of(resourceConfigName, resourceConfig)),
        Matchers.contains(expected));
  }

  @Test
  public void combineParsedResult_duplicatePoolId() {
    String resourceConfigName = "configName";
    PoolConfig poolConfig = new PoolConfig().poolId("id").resourceConfigName(resourceConfigName);
    PoolConfigs pools = new PoolConfigs().poolConfigs(ImmutableList.of(poolConfig, poolConfig));
    ResourceConfig resourceConfig = new ResourceConfig().configName(resourceConfigName);

    assertThrows(
        BadPoolConfigException.class,
        () -> combineParsedConfig(pools, ImmutableMap.of(resourceConfigName, resourceConfig)));
  }

  @Test
  public void combineParsedResult_resourceConfigNotFound() {
    String resourceConfigName = "configName";
    PoolConfig poolConfig = new PoolConfig().poolId("id").resourceConfigName("badName");
    PoolConfigs pools = new PoolConfigs().poolConfigs(ImmutableList.of(poolConfig, poolConfig));
    ResourceConfig resourceConfig = new ResourceConfig().configName(resourceConfigName);

    assertThrows(
        BadPoolConfigException.class,
        () -> combineParsedConfig(pools, ImmutableMap.of(resourceConfigName, resourceConfig)));
  }
}
