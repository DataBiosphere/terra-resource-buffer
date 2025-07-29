package bio.terra.buffer.integration;

import static bio.terra.buffer.integration.IntegrationUtils.StubSubmissionFlightFactory;
import static bio.terra.buffer.integration.IntegrationUtils.blockUntilFlightComplete;
import static bio.terra.buffer.integration.IntegrationUtils.extractResourceIdFromFlightState;
import static bio.terra.buffer.integration.IntegrationUtils.newBasicGcpConfig;
import static bio.terra.buffer.integration.IntegrationUtils.newFullGcpConfig;
import static bio.terra.buffer.integration.IntegrationUtils.preparePool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseIntegrationTest;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.service.resource.FlightManager;
import bio.terra.buffer.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.buffer.service.resource.flight.AssertResourceDeletingStep;
import bio.terra.buffer.service.resource.flight.ErrorStep;
import bio.terra.buffer.service.resource.flight.GoogleProjectDeletionFlight;
import bio.terra.buffer.service.resource.flight.LatchStep;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DeleteProjectFlightIntegrationTest extends BaseIntegrationTest {
  @Autowired BufferDao bufferDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;
  @Autowired TransactionTemplate transactionTemplate;

  @Test
  public void testDeleteGoogleProject_success() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool = preparePool(bufferDao, newFullGcpConfig());

    String createFlightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(
            blockUntilFlightComplete(stairwayComponent, createFlightId));
    Project project = IntegrationUtils.assertProjectExists(bufferDao, rmCow, resourceId);
    Resource resource =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, 1).get(0);

    String deleteFlightId =
        manager.submitDeletionFlight(resource, ResourceType.GOOGLE_PROJECT).get();
    blockUntilFlightComplete(stairwayComponent, deleteFlightId);

    assertProjectDeleting(project.getProjectId());
  }

  @Test
  public void testDeleteGoogleProject_fatalIfHasError() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool = preparePool(bufferDao, newBasicGcpConfig());

    String createFlightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(
            blockUntilFlightComplete(stairwayComponent, createFlightId));
    Resource resource = bufferDao.retrieveResource(resourceId).get();

    // An errors occurs after resource deleted. Expect project is deleted, but we resource state is
    // READY.
    FlightManager errorManager =
        new FlightManager(
            bufferDao,
            new StubSubmissionFlightFactory(ErrorAfterDeleteResourceFlight.class),
            stairwayComponent,
            transactionTemplate);
    String deleteFlightId =
        errorManager.submitDeletionFlight(resource, ResourceType.GOOGLE_PROJECT).get();
    blockUntilFlightComplete(stairwayComponent, deleteFlightId);
    assertEquals(
        FlightStatus.FATAL,
        stairwayComponent.get().getFlightState(deleteFlightId).getFlightStatus());
  }

  @Test
  public void testDeleteGoogleProject_errorWhenResourceStateChange() throws Exception {
    LatchStep.startNewLatch();
    Pool pool = preparePool(bufferDao, newBasicGcpConfig());
    ResourceId resourceId = ResourceId.create(UUID.randomUUID());
    bufferDao.createResource(
        Resource.builder()
            .id(resourceId)
            .poolId(pool.id())
            .creation(BufferDao.currentInstant())
            .state(ResourceState.READY)
            .build());
    Resource resource = bufferDao.retrieveResource(resourceId).get();

    FlightManager manager =
        new FlightManager(
            bufferDao,
            new StubSubmissionFlightFactory(LatchBeforeAssertResourceStep.class),
            stairwayComponent,
            transactionTemplate);
    String deleteFlightId =
        manager.submitDeletionFlight(resource, ResourceType.GOOGLE_PROJECT).get();

    // Delete the resource from DB.
    assertTrue(bufferDao.deleteResource(resource.id()));

    // Release the latch, and resume the flight, assert flight failed.
    LatchStep.releaseLatch();
    extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, deleteFlightId));
    // Resource is deleted.
    assertFalse(bufferDao.retrieveResource(resource.id()).isPresent());
    assertEquals(
        FlightStatus.ERROR,
        stairwayComponent.get().getFlightState(deleteFlightId).getFlightStatus());
  }

  private void assertProjectDeleting(String projectId) throws Exception {
    // Project is ready for deletion
    Project project = rmCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  /** A {@link Flight} with extra error step after resource deletion steps. */
  public static class ErrorAfterDeleteResourceFlight extends GoogleProjectDeletionFlight {
    public ErrorAfterDeleteResourceFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new ErrorStep());
    }
  }

  /** A {@link Flight} that has a {@link LatchStep} before {@link AssertResourceDeletingStep}. */
  public static class LatchBeforeAssertResourceStep extends GoogleProjectDeletionFlight {
    public LatchBeforeAssertResourceStep(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step, RetryRule retryRule) {
      if (step instanceof AssertResourceDeletingStep) {
        addStep(new LatchStep());
      }
      super.addStep(step, retryRule);
    }
  }
}
