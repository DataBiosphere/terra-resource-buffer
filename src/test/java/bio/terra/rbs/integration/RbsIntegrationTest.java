package bio.terra.rbs.integration;

import static bio.terra.rbs.integration.IntegrationUtils.pollUntilResourcesMatch;
import static bio.terra.rbs.service.pool.PoolConfigLoader.loadPoolConfig;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.BaseIntegrationTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.pool.PoolService;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class RbsIntegrationTest extends BaseIntegrationTest {
  @Autowired CloudResourceManagerCow rmCow;

  @Autowired RbsDao rbsDao;
  @Autowired PoolService poolService;

  @Test
  public void testCreateGoogleProject() throws Exception {
    // The pool id in config file.
    PoolId poolId = PoolId.create("ws_test_v1");
    poolService.updateFromConfig(loadPoolConfig("test/config"), null);

    List<Resource> resources = pollUntilResourcesMatch(rbsDao, poolId, ResourceState.CREATING, 2);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(resource.cloudResourceUid());
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
            assertProjectMatch(resource.cloudResourceUid());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });
  }

  private void assertProjectMatch(CloudResourceUid resourceUid) throws Exception {
    Project project =
        rmCow.projects().get(resourceUid.getGoogleProjectUid().getProjectId()).execute();
    assertEquals("ACTIVE", project.getLifecycleState());
  }
}
