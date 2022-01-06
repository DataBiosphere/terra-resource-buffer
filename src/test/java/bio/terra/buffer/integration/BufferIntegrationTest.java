package bio.terra.buffer.integration;

import static bio.terra.buffer.integration.IntegrationUtils.pollUntilResourcesMatch;
import static bio.terra.buffer.service.pool.PoolConfigLoader.loadPoolConfig;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.buffer.common.BaseIntegrationTest;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.db.*;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.service.pool.PoolService;
import bio.terra.buffer.service.pool.PoolWithResourceConfig;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "integration", "integration-enable-scheduler"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class BufferIntegrationTest extends BaseIntegrationTest {
  @Autowired CloudResourceManagerCow rmCow;

  @Autowired BufferDao bufferDao;
  @Autowired PoolService poolService;

  @Test
  public void testCreateGoogleProject() throws Exception {
    // The pool id in config file.
    PoolId poolId = PoolId.create("ws_test_v1");
    List<PoolWithResourceConfig> config = loadPoolConfig("test/config", Optional.empty());
    poolService.updateFromConfig(config);

    List<Resource> resources = pollUntilResourcesMatch(bufferDao, poolId, ResourceState.READY, 2);
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
    bufferDao.updatePoolsSize(ImmutableMap.of(poolId, 5));
    resources = pollUntilResourcesMatch(bufferDao, poolId, ResourceState.READY, 5);
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
    assertEquals("ACTIVE", project.getState());
  }
}
