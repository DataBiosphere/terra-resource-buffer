package bio.terra.buffer.integration;

import static bio.terra.buffer.integration.IntegrationUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.buffer.common.*;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.service.resource.FlightManager;
import bio.terra.buffer.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.buffer.service.resource.flight.*;
import bio.terra.buffer.service.stairway.StairwayComponent;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.*;
import com.google.api.services.cloudresourcemanager.model.Project;
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
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, createFlightId).get();
    ResourceId resourceId = ResourceId.retrieve(resultMap);
    Project project = assertProjectExists(resourceId);
    Resource resource = bufferDao.retrieveResources(pool.id(), ResourceState.READY, 1).get(0);

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
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, createFlightId).get();
    Resource resource = bufferDao.retrieveResource(ResourceId.retrieve(resultMap)).get();

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

  private Project assertProjectExists(ResourceId resourceId) throws Exception {
    Resource resource = bufferDao.retrieveResource(resourceId).get();
    Project project =
        rmCow
            .projects()
            .get(resource.cloudResourceUid().getGoogleProjectUid().getProjectId())
            .execute();
    assertEquals("ACTIVE", project.getLifecycleState());
    return project;
  }

  private void assertProjectDeleting(String projectId) throws Exception {
    // Project is ready for deletion
    Project project = rmCow.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  /** A {@link Flight} with extra error step after resource deletion steps. */
  public static class ErrorAfterDeleteResourceFlight extends GoogleProjectDeletionFlight {
    public ErrorAfterDeleteResourceFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new ErrorStep());
    }
  }
}
