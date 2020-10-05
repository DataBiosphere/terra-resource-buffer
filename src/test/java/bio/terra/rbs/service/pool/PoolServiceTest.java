package bio.terra.rbs.service.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.db.Pool;
import bio.terra.rbs.db.PoolStatus;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.db.ResourceType;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class PoolServiceTest extends BaseUnitTest {
  @Autowired PoolService poolService;

  @Autowired RbsDao rbsDao;

  @Test
  public void initialize() throws Exception {
    poolService.initializeFromConfig();
    List<Pool> pools = rbsDao.retrievePools(PoolStatus.ACTIVE);

    // Expected PoolConfig file is under src/test/resources/config/test folder.
    ResourceConfig expectedResourceConfig =
        new ResourceConfig()
            .configName("aou_ws_resource_v1")
            .gcpProjectConfig(
                new GcpProjectConfig()
                    .projectIDPrefix("aou-rw-test")
                    .enabledApis(ImmutableList.of("bigquery-json.googleapis.com")));

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    assertEquals("aou_ws_test_v1", createdPool.name());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());
    assertEquals(expectedResourceConfig, createdPool.resourceConfig());
  }
}
