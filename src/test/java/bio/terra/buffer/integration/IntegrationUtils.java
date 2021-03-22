package bio.terra.buffer.integration;

import static bio.terra.buffer.generated.model.ProjectIdSchema.SchemeEnum.RANDOM_CHAR;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.*;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.*;
import bio.terra.buffer.service.resource.FlightMapKeys;
import bio.terra.buffer.service.resource.FlightSubmissionFactory;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Utilities used in integration test. */
public class IntegrationUtils {
  /** The folder to create project within in test. */
  public static final String FOLDER_ID = "637867149294";
  /** The billing account to use in test. */
  public static final String BILLING_ACCOUNT_NAME = "01A82E-CA8A14-367457";

  public static final String TEST_CONFIG_NAME = "test_config_v1";

  private static final Duration PERIOD = Duration.ofSeconds(4);
  private static final int MAX_POLL_NUM = 150;

  /**
   * The groups used to test IAM policy sets up on a group. It doesn't matter what the users are for
   * the purpose of this test. They just need to exist for Google. These groups were manually
   * created for Broad development via the BITs service portal.
   */
  private static final String TEST_GROUP_NAME = "terra-rbs-test@broadinstitute.org";

  private static final String TEST_GROUP_VIEWER_NAME = "terra-rbs-viewer-test@broadinstitute.org";

  public static final List<IamBinding> IAM_BINDINGS =
      Arrays.asList(
          new IamBinding().role("roles/editor").addMembersItem("group:" + TEST_GROUP_NAME),
          new IamBinding().role("roles/viewer").addMembersItem("group:" + TEST_GROUP_VIEWER_NAME));

  public static List<Resource> pollUntilResourcesMatch(
      BufferDao bufferDao, PoolId poolId, ResourceState state, int expectedResourceNum)
      throws Exception {
    int numPolls = 0;
    while (numPolls < MAX_POLL_NUM) {
      TimeUnit.MILLISECONDS.sleep(PERIOD.toMillis());
      List<Resource> resources =
          bufferDao.retrieveResourcesRandomly(poolId, state, 10).stream()
              .filter(r -> r.poolId().equals(poolId))
              .collect(Collectors.toList());
      if (resources.size() == expectedResourceNum) {
        return resources;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }

  /** Poll until flight finished, and return {@link FlightState}. */
  public static FlightState blockUntilFlightComplete(
      StairwayComponent stairwayComponent, String flightId)
      throws InterruptedException, DatabaseOperationException {
    Duration maxWait = Duration.ofSeconds(500);
    Duration waited = Duration.ZERO;
    while (waited.compareTo(maxWait) < 0) {
      if (!stairwayComponent.get().getFlightState(flightId).isActive()) {
        return stairwayComponent.get().getFlightState(flightId);
      }
      Duration poll = Duration.ofMillis(4000);
      waited = waited.plus(Duration.ofMillis(poll.toMillis()));
      TimeUnit.MILLISECONDS.sleep(poll.toMillis());
    }
    throw new InterruptedException("Flight did not complete in time.");
  }

  /**
   * Extracts {@link ResourceId} from {@link FlightState}.
   *
   * <p>Stairway convert all input parameter type to String when using FlightState. So we need extra
   * step of converting from String to UUID to ResourceId. See <a
   * href="https://broadworkbench.atlassian.net/browse/PF-316">PF-316</a>
   */
  public static ResourceId extractResourceIdFromFlightState(FlightState flightState) {
    return ResourceId.create(
        UUID.fromString(flightState.getInputParameters().get("ResourceId", String.class)));
  }

  /** Prepares a Pool with {@link GcpProjectConfig}. */
  public static Pool preparePool(BufferDao bufferDao, GcpProjectConfig gcpProjectConfig) {
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(
                new ResourceConfig()
                    .configName(TEST_CONFIG_NAME)
                    .gcpProjectConfig(gcpProjectConfig))
            .status(PoolStatus.ACTIVE)
            .creation(Instant.now())
            .build();
    bufferDao.createPools(ImmutableList.of(pool));
    assertTrue(bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.CREATING, 1).isEmpty());
    assertTrue(bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, 1).isEmpty());
    return pool;
  }

  /** Create a Basic {@link ResourceConfig}. */
  public static GcpProjectConfig newBasicGcpConfig() {
    return new GcpProjectConfig()
        .projectIdSchema(new ProjectIdSchema().prefix("prefix").scheme(RANDOM_CHAR))
        .parentFolderId(FOLDER_ID)
        .billingAccount(BILLING_ACCOUNT_NAME)
        .addEnabledApisItem("compute.googleapis.com")
        .addEnabledApisItem("dns.googleapis.com")
        .addEnabledApisItem("storage-component.googleapis.com");
  }

  /** Create a {@link GcpProjectConfig} with everything enabled. */
  public static GcpProjectConfig newFullGcpConfig() {
    return newBasicGcpConfig()
        .iamBindings(IAM_BINDINGS)
        .network(new bio.terra.buffer.generated.model.Network().enableNetworkMonitoring(true));
  }

  /** A {@link FlightSubmissionFactory} used in test. */
  public static class StubSubmissionFlightFactory implements FlightSubmissionFactory {
    public final Class<? extends Flight> flightClass;

    public StubSubmissionFlightFactory(Class<? extends Flight> flightClass) {
      this.flightClass = flightClass;
    }

    @Override
    public FlightSubmission getCreationFlightSubmission(Pool pool, ResourceId resourceId) {
      FlightMap flightMap = new FlightMap();
      pool.id().store(flightMap);
      resourceId.store(flightMap);
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
