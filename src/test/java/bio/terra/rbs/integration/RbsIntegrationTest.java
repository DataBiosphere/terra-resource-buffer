package bio.terra.rbs.integration;

import static bio.terra.rbs.service.pool.PoolConfigLoader.loadPoolConfig;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.BaseIntegrationTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.pool.PoolService;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.TransactionStatus;

@AutoConfigureMockMvc
public class RbsIntegrationTest extends BaseIntegrationTest {
  @Autowired CloudResourceManagerCow rmCow;

  @Autowired RbsDao rbsDao;
  @Autowired PoolService poolService;

  // Requreid by the method but not used in test.
  TransactionStatus unusedTransactionStatus;

  @Test
  public void testCreateGoogleProject() throws Exception {
    // The pool id in config file.
    PoolId poolId = PoolId.create("ws_test_v1");
    poolService.updateFromConfig(loadPoolConfig("test/config"), unusedTransactionStatus);

    List<Resource> resources =
        pollUntilResourceExists(ResourceState.READY, poolId, 2, Duration.ofSeconds(10), 10);
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
    resources = pollUntilResourceExists(ResourceState.READY, poolId, 5, Duration.ofSeconds(10), 10);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(resource.cloudResourceUid());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });
  }

  private List<Resource> pollUntilResourceExists(
      ResourceState state, PoolId poolId, int expectedResourceNum, Duration period, int maxNumPolls)
      throws Exception {
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      List<Resource> resources =
          rbsDao.retrieveResources(state, 10).stream()
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
