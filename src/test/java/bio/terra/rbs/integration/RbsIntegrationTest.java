package bio.terra.rbs.integration;

import static bio.terra.rbs.integration.IntegrationUtils.pollUntilResourcesMatch;
import static bio.terra.rbs.service.pool.PoolConfigLoader.loadPoolConfig;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.BaseIntegrationTest;
import bio.terra.rbs.common.PoolId;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceState;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.service.pool.PoolService;
import bio.terra.rbs.service.pool.PoolWithResourceConfig;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "integration", "integration-enable-scheduler"})
@AutoConfigureMockMvc
public class RbsIntegrationTest extends BaseIntegrationTest {
  @Autowired CloudResourceManagerCow rmCow;

  @Autowired RbsDao rbsDao;
  @Autowired PoolService poolService;

  @Test
  public void testCreateGoogleProject() throws Exception {
    // The pool id in config file.
    PoolId poolId = PoolId.create("ws_test_v1");
    List<PoolWithResourceConfig> config = loadPoolConfig("test/config");
    poolService.updateFromConfig(config);

    List<Resource> resources = pollUntilResourcesMatch(rbsDao, poolId, ResourceState.READY, 2);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(
                resource.cloudResourceUid(), config.get(0).resourceConfig().getGcpProjectConfig());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });

    // Upgrade the size from 2 to 5. Expect 3 more resources will be created.
    rbsDao.updatePoolsSize(ImmutableMap.of(poolId, 5));
    resources = pollUntilResourcesMatch(rbsDao, poolId, ResourceState.READY, 5);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(
                resource.cloudResourceUid(), config.get(0).resourceConfig().getGcpProjectConfig());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });
  }

  private void assertProjectMatch(CloudResourceUid resourceUid, GcpProjectConfig gcpProjectConfig)
      throws Exception {
    Project project =
        rmCow.projects().get(resourceUid.getGoogleProjectUid().getProjectId()).execute();
    assertEquals("ACTIVE", project.getLifecycleState());
  }
}
