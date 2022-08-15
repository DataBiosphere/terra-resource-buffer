package bio.terra.buffer.service.resource;

import static bio.terra.buffer.common.MetricsHelper.READY_RESOURCE_RATIO_VIEW;
import static bio.terra.buffer.common.MetricsHelper.RESOURCE_STATE_COUNT_VIEW;
import static bio.terra.buffer.common.testing.MetricsTestUtil.assertLastValueDoubleIs;
import static bio.terra.buffer.common.testing.MetricsTestUtil.getPoolIdTag;
import static bio.terra.buffer.common.testing.MetricsTestUtil.getResourceCountTags;
import static bio.terra.buffer.common.testing.MetricsTestUtil.sleepForSpansExport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bio.terra.buffer.app.configuration.PrimaryConfiguration;
import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.common.testing.MetricsTestUtil;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.common.stairway.StairwayComponent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FlightSchedulerTest extends BaseUnitTest {

  // Construct a FlightScheduler manually instead of Autowired for ease of testing.
  private FlightScheduler flightScheduler;
  private final ArgumentCaptor<Resource> resourceArgumentCaptor =
      ArgumentCaptor.forClass(Resource.class);

  @Autowired BufferDao bufferDao;
  @Autowired StairwayComponent stairwayComponent;
  @MockBean FlightManager flightManager;

  private void initializeScheduler() {
    initializeScheduler(newPrimaryConfiguration());
  }

  private void initializeScheduler(PrimaryConfiguration primaryConfiguration) {
    flightScheduler =
        new FlightScheduler(flightManager, primaryConfiguration, stairwayComponent, bufferDao);
    flightScheduler.initialize();
  }

  @AfterEach
  public void tearDown() {
    // Shutdown the FlightScheduler so that it isn't running during other tests.
    if (flightScheduler != null) {
      flightScheduler.shutdown();
    }
  }

  private PrimaryConfiguration newPrimaryConfiguration() {
    PrimaryConfiguration primaryConfiguration = new PrimaryConfiguration();
    primaryConfiguration.setSchedulerEnabled(true);
    primaryConfiguration.setResourceCreationPerPoolLimit(10);
    primaryConfiguration.setResourceDeletionPerPoolLimit(10);
    primaryConfiguration.setDeleteExcessResources(false);
    // Sets submissionPeriod to a big number to make sure it is only runs once.
    primaryConfiguration.setFlightSubmissionPeriod(Duration.ofHours(2));
    return primaryConfiguration;
  }

  /** Creates a pool with resources with given {@code resourceStates}. */
  private Pool newPoolWithResourceCount(int poolSize, Multiset<ResourceState> resourceStates) {
    PoolId poolId = PoolId.create(UUID.randomUUID().toString());
    Pool pool =
        Pool.builder()
            .creation(BufferDao.currentInstant())
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(poolSize)
            .resourceConfig(new ResourceConfig().configName("resourceName"))
            .status(PoolStatus.ACTIVE)
            .build();
    bufferDao.createPools(ImmutableList.of(pool));

    for (ResourceState state : resourceStates) {
      bufferDao.createResource(
          Resource.builder()
              .id(ResourceId.create(UUID.randomUUID()))
              .poolId(poolId)
              .creation(BufferDao.currentInstant())
              .state(state)
              .build());
    }
    return pool;
  }

  @Test
  public void scheduleCreationFlights() throws Exception {
    // Pool1 size 5, should create 2 more resources.
    Pool pool1 =
        newPoolWithResourceCount(
            5,
            ImmutableMultiset.of(ResourceState.READY, ResourceState.READY, ResourceState.CREATING));
    // Pool2 size 3, nothing to create.
    Pool pool2 =
        newPoolWithResourceCount(
            3,
            ImmutableMultiset.of(ResourceState.READY, ResourceState.READY, ResourceState.CREATING));

    initializeScheduler();

    TimeUnit.SECONDS.sleep(4);

    verify(flightManager, times(2)).submitCreationFlight(pool1);
    verify(flightManager, never()).submitCreationFlight(pool2);
    verify(flightManager, never())
        .submitDeletionFlight(any(Resource.class), any(ResourceType.class));
  }

  @Test
  public void scheduleDeactivationFlights_poolDeactivated() throws Exception {
    // pool is delete, should delete the 2 READY resources.
    Pool pool =
        newPoolWithResourceCount(
            5,
            ImmutableMultiset.of(ResourceState.READY, ResourceState.READY, ResourceState.CREATING));

    bufferDao.deactivatePools(ImmutableList.of(pool.id()));
    List<Resource> resources =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, 2);
    initializeScheduler();
    TimeUnit.SECONDS.sleep(4);

    resources.forEach(
        resource ->
            verify(flightManager).submitDeletionFlight(resource, ResourceType.GOOGLE_PROJECT));
    verify(flightManager, never()).submitCreationFlight(any(Pool.class));
  }

  @Test
  public void scheduleDeactivationFlights_enableDeletion() throws Exception {
    newPoolWithResourceCount(1, ImmutableMultiset.of(ResourceState.READY, ResourceState.READY));

    PrimaryConfiguration primaryConfiguration = newPrimaryConfiguration();
    primaryConfiguration.setDeleteExcessResources(true);
    initializeScheduler(primaryConfiguration);

    TimeUnit.SECONDS.sleep(4);

    verify(flightManager)
        .submitDeletionFlight(any(Resource.class), eq(ResourceType.GOOGLE_PROJECT));
  }

  @Test
  public void notScheduleDeactivationFlights_enableDeletionButReadyCountEqualToPoolSize()
      throws Exception {
    // Pool size 1, with 1 ready one creation. Shouldn't deactivate any resources since we only
    // count READY one.
    newPoolWithResourceCount(1, ImmutableMultiset.of(ResourceState.READY, ResourceState.CREATING));

    initializeScheduler();
    TimeUnit.SECONDS.sleep(4);

    verify(flightManager, never())
        .submitDeletionFlight(any(Resource.class), eq(ResourceType.GOOGLE_PROJECT));
    verify(flightManager, never()).submitCreationFlight(any(Pool.class));
  }

  @Test
  public void notScheduleDeactivationFlights_disableDeletion() throws Exception {
    newPoolWithResourceCount(1, ImmutableMultiset.of(ResourceState.READY, ResourceState.READY));

    initializeScheduler();
    TimeUnit.SECONDS.sleep(4);

    verify(flightManager, never())
        .submitDeletionFlight(any(Resource.class), eq(ResourceType.GOOGLE_PROJECT));
    verify(flightManager, never()).submitCreationFlight(any(Pool.class));
  }

  @Test
  public void scheduleCreationFlights_smallerLimitPerExecute() throws Exception {
    // Pool size 10, need to create 7 more resources.
    // Expect number of resourceCreationPerPoolLimit(5) flights are submitted at most.
    Pool pool =
        newPoolWithResourceCount(
            10,
            ImmutableMultiset.of(ResourceState.READY, ResourceState.READY, ResourceState.CREATING));

    PrimaryConfiguration primaryConfiguration = newPrimaryConfiguration();
    primaryConfiguration.setResourceCreationPerPoolLimit(5);
    initializeScheduler(primaryConfiguration);
    TimeUnit.SECONDS.sleep(4);

    verify(flightManager, times(5)).submitCreationFlight(pool);
    verify(flightManager, never())
        .submitDeletionFlight(any(Resource.class), any(ResourceType.class));
  }

  @Test
  public void scheduleDeactivationFlights_smallerLimitPerExecute() throws Exception {
    // Pool1 size 5, need to deactivate the 4 READY resources.
    // Expect number of resourceDeactivationPerPoolLimit(3) flights are submitted at most.
    Pool pool =
        newPoolWithResourceCount(
            5,
            ImmutableMultiset.of(
                ResourceState.READY,
                ResourceState.READY,
                ResourceState.READY,
                ResourceState.READY,
                ResourceState.CREATING));

    bufferDao.deactivatePools(ImmutableList.of(pool.id()));
    List<Resource> resources =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, 4);

    PrimaryConfiguration primaryConfiguration = newPrimaryConfiguration();
    primaryConfiguration.setResourceDeletionPerPoolLimit(3);
    initializeScheduler(primaryConfiguration);

    TimeUnit.SECONDS.sleep(4);

    verify(flightManager, times(3))
        .submitDeletionFlight(resourceArgumentCaptor.capture(), eq(ResourceType.GOOGLE_PROJECT));

    assertThat(
        resources, (Matcher) Matchers.hasItems(resourceArgumentCaptor.getAllValues().toArray()));
    verify(flightManager, never()).submitCreationFlight(any(Pool.class));
  }

  @Test
  public void testRecordResourceState() throws Exception {
    // activatePool has 2 READY, 1 creating.
    Pool activatePool =
        newPoolWithResourceCount(
            5,
            ImmutableMultiset.of(ResourceState.READY, ResourceState.READY, ResourceState.CREATING));
    // deactivatedPool has 1 READY and 2 CREATING resources.
    Pool deactivatedPool =
        newPoolWithResourceCount(
            5,
            ImmutableMultiset.of(
                ResourceState.READY, ResourceState.CREATING, ResourceState.CREATING));
    bufferDao.deactivatePools(ImmutableList.of(deactivatedPool.id()));

    initializeScheduler();
    sleepForSpansExport();

    MetricsTestUtil.assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(activatePool.id(), ResourceState.READY, PoolStatus.ACTIVE),
        2);
    MetricsTestUtil.assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(activatePool.id(), ResourceState.CREATING, PoolStatus.ACTIVE),
        1);
    MetricsTestUtil.assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(deactivatedPool.id(), ResourceState.READY, PoolStatus.DEACTIVATED),
        1);
    MetricsTestUtil.assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(deactivatedPool.id(), ResourceState.CREATING, PoolStatus.DEACTIVATED),
        2);
    // activate pool ratio is 2/5. Deactivated pool is not recorded.
    assertLastValueDoubleIs(
        READY_RESOURCE_RATIO_VIEW.getName(), getPoolIdTag(activatePool.id()), 0.40);
    assertLastValueDoubleIs(
        READY_RESOURCE_RATIO_VIEW.getName(), getPoolIdTag(deactivatedPool.id()), 1);
  }
}
