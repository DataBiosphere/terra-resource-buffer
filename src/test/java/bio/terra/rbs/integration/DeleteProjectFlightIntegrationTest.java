package bio.terra.rbs.integration;

import static bio.terra.rbs.integration.IntegrationUtils.*;
import static bio.terra.rbs.service.resource.flight.CreateRouteStep.*;
import static bio.terra.rbs.service.resource.flight.CreateSubnetsStep.*;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.*;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.service.resource.FlightManager;
import bio.terra.rbs.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.rbs.service.resource.flight.*;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class DeleteProjectFlightIntegrationTest extends BaseIntegrationTest {
  @Autowired RbsDao rbsDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;

  @Test
  public void testDeleteGoogleProject_success() throws Exception {
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool =
        preparePool(
            rbsDao,
            newBasicGcpConfig()
                .network(
                    new bio.terra.rbs.generated.model.Network().enableNetworkMonitoring(true)));

    String createFlightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, createFlightId).get();
    ResourceId resourceId = ResourceId.retrieve(resultMap);
    Project project = assertProjectExists(resourceId);
    Resource resource = rbsDao.retrieveResources(pool.id(), ResourceState.READY, 1).get(0);

    String deleteFlightId =
        manager.submitDeletionFlight(resource, ResourceType.GOOGLE_PROJECT).get();
    blockUntilFlightComplete(stairwayComponent, deleteFlightId);

    assertProjectDeleting(project.getProjectId());
  }

  @Test
  public void testDeleteGoogleProject_fatalIfHasError() throws Exception {
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool = preparePool(rbsDao, newBasicGcpConfig());

    String createFlightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, createFlightId).get();
    Resource resource = rbsDao.retrieveResource(ResourceId.retrieve(resultMap)).get();

    // An errors occurs after resource deleted. Expect project is deleted, but we resource state is
    // READY.
    FlightManager errorManager =
        new FlightManager(
            new StubSubmissionFlightFactory(ErrorAfterDeleteResourceFlight.class),
            stairwayComponent);
    String deleteFlightId =
        errorManager.submitDeletionFlight(resource, ResourceType.GOOGLE_PROJECT).get();
    blockUntilFlightComplete(stairwayComponent, deleteFlightId);
    assertEquals(
        FlightStatus.FATAL,
        stairwayComponent.get().getFlightState(deleteFlightId).getFlightStatus());
  }

  private Project assertProjectExists(ResourceId resourceId) throws Exception {
    Resource resource = rbsDao.retrieveResource(resourceId).get();
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
