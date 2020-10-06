package bio.terra.rbs.service.pool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.TransactionStatus;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class PoolServiceTest extends BaseUnitTest {
  @Autowired PoolService poolService;
  @Autowired RbsDao rbsDao;
  TransactionStatus transactionStatus;

  private static final String RESOURCE_CONFIG_NAME = "aou_ws_resource_v1";

  private static ResourceConfig newResourceConfig(GcpProjectConfig gcpProjectConfig) {
    return new ResourceConfig().configName(RESOURCE_CONFIG_NAME).gcpProjectConfig(gcpProjectConfig);
  }

  @Test
  public void updateFromConfig_createPool() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    ResourceConfig resourceConfig =
        newResourceConfig(
            new GcpProjectConfig()
                .projectIDPrefix("aou-rw-test")
                .enabledApis(ImmutableList.of("bigquery-json.googleapis.com")));
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            resourceConfig);

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig), transactionStatus);
    List<Pool> pools = rbsDao.retrievePools();

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());
    assertEquals(resourceConfig, createdPool.resourceConfig());
  }

  @Test
  public void updateFromConfig_alreadyExists() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    ResourceConfig resourceConfig =
        newResourceConfig(
            new GcpProjectConfig()
                .projectIDPrefix("aou-rw-test")
                .enabledApis(ImmutableList.of("bigquery-json.googleapis.com")));
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            resourceConfig);

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig), transactionStatus);
    List<Pool> pools = rbsDao.retrievePools();

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());
    assertEquals(resourceConfig, createdPool.resourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig), transactionStatus);
    assertThat(rbsDao.retrievePools(), Matchers.containsInAnyOrder(pools.toArray()));
  }

  @Test
  public void updateFromConfig_updateResourceConfigOnExistingPool_throwsException()
      throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolConfig poolConfig =
        new PoolConfig().poolId(poolId.toString()).size(1).resourceConfigName(RESOURCE_CONFIG_NAME);
    ResourceConfig resourceConfig =
        newResourceConfig(new GcpProjectConfig().projectIDPrefix("aou-rw-test1"));
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(poolConfig, resourceConfig);
    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig), transactionStatus);

    // Sets ResourceConfig's GCP project id prefix to newer value.
    PoolWithResourceConfig updatedPoolConfig =
        PoolWithResourceConfig.create(
            poolConfig, newResourceConfig(new GcpProjectConfig().projectIDPrefix("aou-rw-test2")));

    assertThrows(
        RuntimeException.class,
        () -> poolService.updateFromConfig(ImmutableList.of(updatedPoolConfig), transactionStatus));
    assertThat(
        rbsDao.retrievePools().stream().map(Pool::resourceConfig).collect(Collectors.toList()),
        Matchers.containsInAnyOrder(resourceConfig));
  }

  @Test
  public void updateFromConfig_duplicatePoolId() throws Exception {
    // TODO: Implement this once Dao support update status.
  }
}
