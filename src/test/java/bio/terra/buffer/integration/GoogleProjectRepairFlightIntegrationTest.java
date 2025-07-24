package bio.terra.buffer.integration;

import static bio.terra.buffer.integration.IntegrationUtils.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.*;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.service.resource.FlightManager;
import bio.terra.buffer.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.common.stairway.StairwayComponent;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.api.services.serviceusage.v1beta1.model.Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GoogleProjectRepairFlightIntegrationTest extends BaseIntegrationTest {
  @Autowired BufferDao bufferDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired ServiceUsageCow serviceUsageCow;

  @Test
  public void testRepairFlight_enablesApis() throws Exception {
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
    String apiToEnable = gcpProjectConfig.getEnabledApis().get(0);
//    serviceUsageCow.services().disable(projectId, apiToEnable).execute();

    String repairFlightId = manager.submitRepairResourceFlight(pool, googleProjectId).get();
    blockUntilFlightComplete(stairwayComponent, repairFlightId);

    // Assert API is enabled
    boolean apiEnabled = serviceUsageCow
            .services()
            .list("projects/" + googleProjectId.getProjectId())
            .setFilter("state:ENABLED")
            .execute()
            .getServices()
            .stream()
            .map(Service::getName)
            .anyMatch(name -> name.endsWith(apiToEnable));
    assertTrue(apiEnabled, "API should be enabled after repair flight");
  }
}