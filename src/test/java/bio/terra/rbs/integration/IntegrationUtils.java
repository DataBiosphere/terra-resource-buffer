package bio.terra.rbs.integration;

import bio.terra.rbs.common.PoolId;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceState;
import bio.terra.rbs.db.RbsDao;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.serviceusage.v1.model.GoogleApiServiceusageV1Service;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Utilities used in integration test. */
public class IntegrationUtils {
  private static final Duration PERIOD = Duration.ofSeconds(10);
  private static final int MAX_POLL_NUM = 20;

  public static List<Resource> pollUntilResourcesMatch(
      RbsDao rbsDao, PoolId poolId, ResourceState state, int expectedResourceNum) throws Exception {
    int numPolls = 0;
    while (numPolls < MAX_POLL_NUM) {
      TimeUnit.MILLISECONDS.sleep(PERIOD.toMillis());
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

  /**
   * Create a string matching the service name on {@link GoogleApiServiceusageV1Service#getName()},
   * e.g. projects/123/services/serviceusage.googleapis.com.
   */
  public static String serviceName(Project project, String apiId) {
    return String.format("projects/%d/services/%s", project.getProjectNumber(), apiId);
  }
}
