package bio.terra.rbs.integration;

import bio.terra.rbs.db.PoolId;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.db.Resource;
import bio.terra.rbs.db.ResourceState;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Utilities used in integration test. */
public class IntegrationUtils {
  private static final Duration PERIOD = Duration.ofSeconds(5);
  private static final int MAX_POLL_NUM = 10;

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
}
