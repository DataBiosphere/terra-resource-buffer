package bio.terra.rbs.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.BaseIntegrationTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class RbsIntegrationTest extends BaseIntegrationTest {
  @Autowired CloudResourceManagerCow rmCow;

  @Autowired RbsDao rbsDao;

  @Test
  public void testCreateGoogleProject() throws Exception {
    PoolId poolId = PoolId.create("ws_test_v1");
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("ws_config")
            .gcpProjectConfig(new GcpProjectConfig().parentFolderId("637867149294"));
    Pool pool =
        Pool.builder()
            .id(poolId)
            .status(PoolStatus.ACTIVE)
            .size(2)
            .creation(Instant.now())
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .resourceConfig(resourceConfig)
            .build();
    rbsDao.createPools(ImmutableList.of(pool));

    List<Resource> resources = pollUntilResourceCreated(poolId, 2, Duration.ofSeconds(10), 10);
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
    resources = pollUntilResourceCreated(poolId, 5, Duration.ofSeconds(10), 10);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(resource.cloudResourceUid());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });
  }

  private List<Resource> pollUntilResourceCreated(
      PoolId poolId, int expectedResourceNum, Duration period, int maxNumPolls) throws Exception {
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      List<Resource> resources =
          rbsDao.retrieveResources(ResourceState.READY, 10).stream()
              .filter(r -> r.poolId().equals(poolId))
              .collect(Collectors.toList());
      if (resources.size() == expectedResourceNum) {
        return resources;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }

  private void assertProjectMatch(CloudResourceUid resourceUid) throws Exception {
    Project project =
        rmCow.projects().get(resourceUid.getGoogleProjectUid().getProjectId()).execute();
    assertEquals("ACTIVE", project.getLifecycleState());
  }
}
