package bio.terra.rbs.service.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.*;
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
  public void initialize_createPool() throws Exception {
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

    poolService.initializeFromConfig(ImmutableList.of(parsedPoolConfig), transactionStatus);
    List<Pool> pools = rbsDao.retrievePools();

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());
    assertEquals(resourceConfig, createdPool.resourceConfig());
  }

  @Test
  public void initialize_resourceConfigUpdate() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .creation(Instant.now())
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(
                newResourceConfig(new GcpProjectConfig().projectIDPrefix("aou-rw-test")))
            .status(PoolStatus.ACTIVE)
            .build();

    rbsDao.createPools(ImmutableList.of(pool));

    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig(new GcpProjectConfig().projectIDPrefix("aou-rw-test111")));

    assertThrows(
        RuntimeException.class,
        () ->
            poolService.initializeFromConfig(
                ImmutableList.of(parsedPoolConfig), transactionStatus));

    assertEquals(rbsDao.retrievePools().get(0), pool);
  }
}
