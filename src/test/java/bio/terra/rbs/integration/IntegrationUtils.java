package bio.terra.rbs.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.rbs.common.*;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.rbs.service.resource.FlightSubmissionFactory;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Utilities used in integration test. */
public class IntegrationUtils {
  /** The folder to create project within in test. */
  public static final String FOLDER_ID = "637867149294";
  /** The billing account to use in test. */
  public static final String BILLING_ACCOUNT_NAME = "01A82E-CA8A14-367457";

  private static final Duration PERIOD = Duration.ofSeconds(10);
  private static final int MAX_POLL_NUM = 40;

  public static List<Resource> pollUntilResourcesMatch(
      RbsDao rbsDao, PoolId poolId, ResourceState state, int expectedResourceNum) throws Exception {
    int numPolls = 0;
    while (numPolls < MAX_POLL_NUM) {
      TimeUnit.MILLISECONDS.sleep(PERIOD.toMillis());
      List<Resource> resources =
          rbsDao.retrieveResources(poolId, state, 10).stream()
              .filter(r -> r.poolId().equals(poolId))
              .collect(Collectors.toList());
      if (resources.size() == expectedResourceNum) {
        return resources;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }

  public static void blockUntilFlightComplete(StairwayComponent stairwayComponent, String flightId)
      throws InterruptedException, DatabaseOperationException {
    Duration maxWait = Duration.ofSeconds(10);
    Duration waited = Duration.ZERO;
    while (waited.compareTo(maxWait) < 0) {
      if (!stairwayComponent.get().getFlightState(flightId).isActive()) {
        System.out.println("~~~~~~~~~~~~~~getResultMap");
        System.out.println("stairwayComponent.get().getFlightState(flightId).getResultMap()");
        return;
      }
      Duration poll = Duration.ofMillis(100);
      waited.plus(Duration.ofMillis(poll.toMillis()));
      TimeUnit.MILLISECONDS.sleep(poll.toMillis());
    }
    throw new InterruptedException("Flight did not complete in time.");
  }

  /** Prepares a Pool with {@link GcpProjectConfig}. */
  public static Pool preparePool(RbsDao rbsDao, GcpProjectConfig gcpProjectConfig) {
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(
                new ResourceConfig().configName("configName").gcpProjectConfig(gcpProjectConfig))
            .status(PoolStatus.ACTIVE)
            .creation(Instant.now())
            .build();
    rbsDao.createPools(ImmutableList.of(pool));
    assertTrue(rbsDao.retrieveResources(pool.id(), ResourceState.CREATING, 1).isEmpty());
    assertTrue(rbsDao.retrieveResources(pool.id(), ResourceState.READY, 1).isEmpty());
    return pool;
  }

  /** Create a Basic {@link ResourceConfig}. */
  public static GcpProjectConfig newBasicGcpConfig() {
    return new GcpProjectConfig()
        .projectIDPrefix("prefix")
        .parentFolderId(FOLDER_ID)
        .billingAccount(BILLING_ACCOUNT_NAME)
        .addEnabledApisItem("compute.googleapis.com");
  }

  /** A {@link FlightSubmissionFactory} used in test. */
  public static class StubSubmissionFlightFactory implements FlightSubmissionFactory {
    public final Class<? extends Flight> flightClass;

    public StubSubmissionFlightFactory(Class<? extends Flight> flightClass) {
      this.flightClass = flightClass;
    }

    @Override
    public FlightSubmission getCreationFlightSubmission(Pool pool) {
      FlightMap flightMap = new FlightMap();
      pool.id().store(flightMap);
      flightMap.put(FlightMapKeys.RESOURCE_CONFIG, pool.resourceConfig());
      return FlightSubmission.create(flightClass, flightMap);
    }

    @Override
    public FlightSubmission getDeletionFlightSubmission(Resource resource, ResourceType type) {
      FlightMap flightMap = new FlightMap();
      resource.id().store(flightMap);
      flightMap.put(FlightMapKeys.CLOUD_RESOURCE_UID, resource.cloudResourceUid());
      return FlightSubmission.create(flightClass, flightMap);
    }
  }
}
