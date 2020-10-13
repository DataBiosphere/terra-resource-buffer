package bio.terra.rbs.integration;

import static bio.terra.rbs.app.configuration.BeanNames.GOOGLE_RM_COW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.BaseIntegrationTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
public class RbsIntegrationTest extends BaseIntegrationTest {
  // The AOU pool id defined in pool config.
  // Currently
  private static PoolId AOU_POOL_ID = PoolId.create("aou_ws_test_v1");

  @Autowired
  @Qualifier(GOOGLE_RM_COW)
  CloudResourceManagerCow rmCow;

  @Autowired RbsDao rbsDao;

  @Test
  public void testCreateGoogleProject() throws Exception {
    List<Resource> resources = pollUntilResourceCreated(AOU_POOL_ID, 2, Duration.ofSeconds(5), 10);
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
