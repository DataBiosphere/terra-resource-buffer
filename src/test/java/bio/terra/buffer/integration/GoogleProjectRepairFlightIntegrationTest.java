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

import java.util.List;

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

    // Assert permissions were removed
    com.google.api.services.cloudresourcemanager.v3.model.Policy policy =
            rmCow.projects().getIamPolicy(project.getProjectId(), new com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest()).execute();

    com.google.auth.oauth2.GoogleCredentials credentials = com.google.auth.oauth2.GoogleCredentials.getApplicationDefault();
    String serviceAccountEmail = ((com.google.auth.oauth2.ServiceAccountCredentials) credentials).getClientEmail();
    String member = "serviceAccount:" + serviceAccountEmail;
    List<String> rolesToRemove = List.of("roles/serviceusage.serviceUsageAdmin", "roles/resourcemanager.projectIamAdmin");

    boolean hasBinding = policy.getBindings().stream()
            .filter(b -> rolesToRemove.contains(b.getRole()))
            .anyMatch(b -> b.getMembers() != null && b.getMembers().contains(member));
    assertTrue(!hasBinding, "Service account should not have the removed roles");
  }
}