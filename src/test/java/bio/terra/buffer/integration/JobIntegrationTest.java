package bio.terra.buffer.integration;

import static bio.terra.buffer.integration.IntegrationUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseIntegrationTest;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.SqlSortDirection;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.service.job.JobService;
import bio.terra.buffer.service.resource.FlightManager;
import bio.terra.buffer.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.common.stairway.StairwayComponent;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class JobIntegrationTest extends BaseIntegrationTest {
  @Autowired BufferDao bufferDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired JobService jobService;

  String CREATE_RESOURCE_FLIGHT_NAME =
      "bio.terra.buffer.service.resource.flight.GoogleProjectCreationFlight";
  String REPAIR_RESOURCE_FLIGHT_NAME =
      "bio.terra.buffer.service.resource.flight.GoogleProjectRepairFlight";

  // @Test
  public void testEnumerateJobs() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    GcpProjectConfig gcpProjectConfig = newFullGcpConfig();
    Pool pool = preparePool(bufferDao, gcpProjectConfig);

    String createFlightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(
            blockUntilFlightComplete(stairwayComponent, createFlightId));

    Project project = IntegrationUtils.assertProjectExists(bufferDao, rmCow, resourceId);
    GoogleProjectUid googleProjectId = new GoogleProjectUid().projectId(project.getProjectId());

    String repairFlightId = manager.submitRepairResourceFlight(pool, googleProjectId).get();
    blockUntilFlightComplete(stairwayComponent, repairFlightId);

    List<JobModel> allJobs =
        jobService.enumerateJobs(0, 10, SqlSortDirection.DESC, null, null);
    assertEquals(2, allJobs.size());
    List<String> allJobClasses = allJobs.stream().map(JobModel::getClassName).toList();
    assertTrue(
        allJobClasses.containsAll(
            List.of(CREATE_RESOURCE_FLIGHT_NAME, REPAIR_RESOURCE_FLIGHT_NAME)));

    List<JobModel> repairResourceJobs =
        jobService.enumerateJobs(0, 10, SqlSortDirection.DESC, REPAIR_RESOURCE_FLIGHT_NAME, null);
    assertEquals(1, repairResourceJobs.size());
    List<String> repairResourceClassNames =
        repairResourceJobs.stream().map(JobModel::getClassName).toList();
    assertTrue(repairResourceClassNames.contains(REPAIR_RESOURCE_FLIGHT_NAME));

    List<JobModel> googleProjectIdJobs =
        jobService.enumerateJobs(
            0,
            10,
            SqlSortDirection.DESC,
            null,
            List.of("googleProjectId=" + googleProjectId.getProjectId()));
    assertEquals(1, googleProjectIdJobs.size());
    // Inputs are not a part of the JobModel, so we cannot assert on them directly.
    List<String> result = repairResourceJobs.stream().map(JobModel::getId).toList();
    assertTrue(result.contains(repairFlightId));
  }
}
