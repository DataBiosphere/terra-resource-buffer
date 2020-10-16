package bio.terra.rbs.integration;

import static bio.terra.rbs.integration.IntegrationUtils.pollUntilResourcesMatch;
import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.*;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.rbs.service.resource.FlightManager;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.rbs.service.resource.FlightSubmissionFactory;
import bio.terra.rbs.service.resource.flight.*;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class CreateProjectFlightIntegrationTest extends BaseIntegrationTest {
  /** The folder to create project within in test. */
  private static final String FOLDER_ID = "637867149294";

  @Autowired RbsDao rbsDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudResourceManagerCow rmCow;

  FlightSubmissionFactory flightSubmissionFactory = new TestingSubmissionFlightFactory();

  @Test
  public void testCreateGoogleProject_errorDuringProjectCreation() throws Exception {
    // Verify flight is able to successfully rollback when project fails to create and doesn't
    // exist.
    TestingSubmissionFlightFactory.setFlightClassToUse(ErrorCreateProjectFlight.class);
    LatchStep.startNewLatch();

    FlightManager manager = new FlightManager(flightSubmissionFactory, stairwayComponent);
    Pool pool = preparePool();

    rbsDao.createPools(ImmutableList.of(pool));
    assertTrue(rbsDao.retrieveResources(ResourceState.CREATING, 1).isEmpty());
    String flightId = manager.submitCreationFlight(pool).get();
    // Resource is created in db
    Resource resource =
        pollUntilResourcesMatch(rbsDao, pool.id(), ResourceState.CREATING, 1).get(0);

    LatchStep.releaseLatch();
    blockUntilFlightComplete(flightId);
    // Resource is deleted.
    assertFalse(rbsDao.retrieveResource(resource.id()).isPresent());
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(flightId).getFlightStatus());
  }

  @Test
  public void errorCreateProject_noRollbackAfterResourceReady() throws Exception {
    // Verify project and db entity won't get deleted if resource id READY, even the flight fails.
    TestingSubmissionFlightFactory.setFlightClassToUse(ErrorAfterCreateResourceFlight.class);
    FlightManager manager = new FlightManager(flightSubmissionFactory, stairwayComponent);

    Pool pool = preparePool();
    rbsDao.createPools(ImmutableList.of(pool));
    assertTrue(rbsDao.retrieveResources(ResourceState.CREATING, 1).isEmpty());
    assertTrue(rbsDao.retrieveResources(ResourceState.READY, 1).isEmpty());
    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);

    Resource resource = rbsDao.retrieveResources(ResourceState.READY, 1).get(0);
    assertEquals(
        "ACTIVE",
        rmCow
            .projects()
            .get(resource.cloudResourceUid().getGoogleProjectUid().getProjectId())
            .execute()
            .getLifecycleState());
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(flightId).getFlightStatus());
  }

  /** A {@link Flight} that will fail to create Google Project. */
  public static class ErrorCreateProjectFlight extends Flight {
    public ErrorCreateProjectFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      RbsDao rbsDao = ((ApplicationContext) applicationContext).getBean(RbsDao.class);
      CloudResourceManagerCow rmCow =
          ((ApplicationContext) applicationContext).getBean(CloudResourceManagerCow.class);
      GcpProjectConfig gcpProjectConfig =
          inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();
      addStep(new GenerateResourceIdStep());
      addStep(new CreateResourceDbEntityStep(rbsDao));
      addStep(new LatchStep());
      addStep(new GenerateProjectIdStep());
      addStep(new ErrorCreateProjectStep(rmCow, gcpProjectConfig));
      addStep(new FinishResourceCreationStep(rbsDao));
    }
  }

  /** A {@link Flight} with extra error step after resource creation steps. */
  public static class ErrorAfterCreateResourceFlight extends GoogleProjectCreationFlight {
    public ErrorAfterCreateResourceFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new ErrorStep());
    }
  }

  private void blockUntilFlightComplete(String flightId)
      throws InterruptedException, DatabaseOperationException {
    Duration maxWait = Duration.ofSeconds(10);
    Duration waited = Duration.ZERO;
    while (waited.compareTo(maxWait) < 0) {
      if (!stairwayComponent.get().getFlightState(flightId).isActive()) {
        return;
      }
      Duration poll = Duration.ofMillis(100);
      waited.plus(Duration.ofMillis(poll.toMillis()));
      TimeUnit.MILLISECONDS.sleep(poll.toMillis());
    }
    throw new InterruptedException("Flight did not complete in time.");
  }

  /** Create a Pool in db. */
  private Pool preparePool() {
    PoolId poolId = PoolId.create("poolId");
    return Pool.builder()
        .id(poolId)
        .resourceType(ResourceType.GOOGLE_PROJECT)
        .size(1)
        .resourceConfig(
            new ResourceConfig()
                .configName("configName")
                .gcpProjectConfig(
                    new GcpProjectConfig().projectIDPrefix("prefix").parentFolderId(FOLDER_ID)))
        .status(PoolStatus.ACTIVE)
        .creation(Instant.now())
        .build();
  }
  /** A {@link FlightSubmissionFactory} used in test. */
  public static class TestingSubmissionFlightFactory implements FlightSubmissionFactory {
    public static Class<? extends Flight> flightClass;

    public static void setFlightClassToUse(Class<? extends Flight> clazz) {
      flightClass = clazz;
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
      return FlightSubmission.create(flightClass, new FlightMap());
    }
  }

  /** Dummy {@link CreateProjectStep} which fails in doStep but still runs undoStep. */
  public static class ErrorCreateProjectStep extends CreateProjectStep {
    public ErrorCreateProjectStep(
        CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
      super(rmCow, gcpProjectConfig);
    }

    @Override
    public StepResult doStep(FlightContext flightContext) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
    }
  }
}
