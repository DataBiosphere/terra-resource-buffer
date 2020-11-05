package bio.terra.rbs.integration;

import static bio.terra.rbs.integration.IntegrationUtils.*;
import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.rbs.service.resource.flight.CreateDnsZoneStep.MANAGED_ZONE_TEMPLATE;
import static bio.terra.rbs.service.resource.flight.CreateFirewallRuleStep.*;
import static bio.terra.rbs.service.resource.flight.CreateResourceRecordSetStep.A_RECORD;
import static bio.terra.rbs.service.resource.flight.CreateResourceRecordSetStep.CNAME_RECORD;
import static bio.terra.rbs.service.resource.flight.CreateRouteStep.*;
import static bio.terra.rbs.service.resource.flight.CreateSubnetsStep.*;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.rbs.common.*;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.IamBinding;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.rbs.service.resource.FlightManager;
import bio.terra.rbs.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.rbs.service.resource.flight.*;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Route;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.dns.model.ManagedZone;
import com.google.api.services.dns.model.ResourceRecordSet;
import com.google.api.services.serviceusage.v1.model.GoogleApiServiceusageV1Service;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CreateProjectFlightIntegrationTest extends BaseIntegrationTest {
  @Autowired RbsDao rbsDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudComputeCow computeCow;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired CloudBillingClientCow billingCow;
  @Autowired DnsCow dnsCow;
  @Autowired ServiceUsageCow serviceUsageCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;

  enum NetworkMonitoring {
    ENABLED,
    DISABLED
  }

  @Test
  public void testCreateGoogleProject_basicCreation() throws Exception {
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool = preparePool(rbsDao, newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, flightId).get();
    Project project = assertProjectExists(ResourceId.retrieve(resultMap));
    assertBillingIs(project, pool.resourceConfig().getGcpProjectConfig().getBillingAccount());
    assertEnableApisContains(project, pool.resourceConfig().getGcpProjectConfig().getEnabledApis());
    assertNetworkExists(project);
    assertFirewallRulesExist(project);
    assertSubnetsExist(project, NetworkMonitoring.DISABLED);
    assertRouteNotExists(project);
    assertDnsNotExists(project);
  }

  @Test
  public void testCreateGoogleProject_witIamBindings() throws Exception {
    // Basic GCP project with IAM Bindings
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool = preparePool(rbsDao, newBasicGcpConfig().iamBindings(IAM_BINDINGS));

    String flightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, flightId).get();

    Project project = assertProjectExists(ResourceId.retrieve(resultMap));
    assertIamBindingsContains(project, IAM_BINDINGS);
  }

  @Test
  public void testCreateGoogleProject_enableNetworkMonitoring() throws Exception {
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool =
        preparePool(
            rbsDao,
            newBasicGcpConfig()
                .network(
                    new bio.terra.rbs.generated.model.Network().enableNetworkMonitoring(true)));

    String flightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, flightId).get();
    Project project = assertProjectExists(ResourceId.retrieve(resultMap));
    assertNetworkExists(project);
    assertSubnetsExist(project, NetworkMonitoring.ENABLED);
    assertRouteExists(project);
    assertDnsExists(project);
  }

  @Test
  public void testCreateGoogleProject_multipleSteps() throws Exception {
    // Verify flight is able to finish with multiple same steps exists.
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(MultiInstanceStepFlight.class), stairwayComponent);
    Pool pool = preparePool(rbsDao, newFullGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, flightId).get();
    Project project = assertProjectExists(ResourceId.retrieve(resultMap));
    assertIamBindingsContains(project, IAM_BINDINGS);
    assertNetworkExists(project);
    assertSubnetsExist(project, NetworkMonitoring.ENABLED);
    assertRouteExists(project);
    assertDnsExists(project);
  }

  @Test
  public void testCreateGoogleProject_errorDuringProjectCreation() throws Exception {
    // Verify flight is able to successfully rollback when project fails to create and doesn't
    // exist.
    LatchStep.startNewLatch();
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(ErrorCreateProjectFlight.class), stairwayComponent);
    Pool pool = preparePool(rbsDao, newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    // Resource is created in db
    Resource resource =
        pollUntilResourcesMatch(rbsDao, pool.id(), ResourceState.CREATING, 1).get(0);

    LatchStep.releaseLatch();
    blockUntilFlightComplete(stairwayComponent, flightId).get();
    // Resource is deleted.
    assertFalse(rbsDao.retrieveResource(resource.id()).isPresent());
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(flightId).getFlightStatus());
  }

  @Test
  public void errorCreateProject_noRollbackAfterResourceReady() throws Exception {
    // Verify project and db entity won't get deleted if resource id READY, even the flight fails.
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(ErrorAfterCreateResourceFlight.class),
            stairwayComponent);

    Pool pool = preparePool(rbsDao, newBasicGcpConfig());
    String flightId = manager.submitCreationFlight(pool).get();
    FlightMap resultMap = blockUntilFlightComplete(stairwayComponent, flightId).get();

    Resource resource = rbsDao.retrieveResources(pool.id(), ResourceState.READY, 1).get(0);
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

  /**
   * A sub-flight class of {@link GoogleProjectCreationFlight} which inserts some steps twice.
   *
   * <p>This class can verify those duplicated steps still succeed in "retry after succeed" cases,
   * e.g., when creating Network, polling operation result timeout, but Network is created when the
   * step is retried.
   */
  public static class MultiInstanceStepFlight extends GoogleProjectCreationFlight {
    /**
     * Steps that doesn't need to handle "retry after succeed" scenario, if duplicates happens, the flight will fail
     * instead of success.
     */
    private static final List<Class<? extends Step>> SKIP_DUP_CHECK_STEP_CLAZZ =
        ImmutableList.of(CreateResourceDbEntityStep.class, CreateProjectStep.class);

    public MultiInstanceStepFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step) {
      super.addStep(step);

      if (!SKIP_DUP_CHECK_STEP_CLAZZ.stream().anyMatch(clazz -> clazz.isInstance(step))) {
        super.addStep(step);
      }
    }
  }

  /** A {@link Flight} with extra error step after resource creation steps. */
  public static class ErrorAfterCreateResourceFlight extends GoogleProjectCreationFlight {
    public ErrorAfterCreateResourceFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new ErrorStep());
    }
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

  private void assertBillingIs(Project project, String billingAccount) {
    assertEquals(
        "billingAccounts/" + billingAccount,
        billingCow
            .getProjectBillingInfo(projectIdToName(project.getProjectId()))
            .getBillingAccountName());
  }

  private void assertEnableApisContains(Project project, List<String> enabledApis)
      throws Exception {
    List<String> serviceNames =
        enabledApis.stream()
            .map(apiName -> serviceName(project, apiName))
            .collect(Collectors.toList());
    assertThat(
        serviceUsageCow.services().list(projectIdToName(project.getProjectId()))
            .setFilter("state:ENABLED").execute().getServices().stream()
            .map(GoogleApiServiceusageV1Service::getName)
            .collect(Collectors.toList()),
        Matchers.hasItems(serviceNames.toArray()));
  }

  private void assertIamBindingsContains(Project project, List<IamBinding> iamBindings)
      throws Exception {
    // By default we enable some services, which causes GCP to automatically create Service Accounts
    // and grant them permissions on the project.
    // e.g.,"serviceAccount:{projectId}-compute@developer.gserviceaccount.com" has editor role.
    // So we need to iterate through all bindings and verify they at least contain the members &
    // roles we expect.

    Map<String, List<String>> allBindings =
        rmCow.projects().getIamPolicy(project.getProjectId(), new GetIamPolicyRequest()).execute()
            .getBindings().stream()
            .collect(Collectors.toMap(Binding::getRole, Binding::getMembers));

    for (IamBinding iamBinding : iamBindings) {
      assertThat(
          new ArrayList<>(allBindings.get(iamBinding.getRole())),
          Matchers.hasItems(iamBinding.getMembers().toArray()));
    }
  }

  private void assertNetworkExists(Project project) throws Exception {
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    assertFalse(network.getAutoCreateSubnetworks());
  }

  private void assertFirewallRulesExist(Project project) throws Exception {
    String projectId = project.getProjectId();
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    Firewall allowInternal =
        computeCow.firewalls().get(projectId, ALLOW_INTERNAL.getName()).execute();
    Firewall leonardoSsl = computeCow.firewalls().get(projectId, LEONARDO_SSL.getName()).execute();

    assertFirewallRuleMatch(ALLOW_INTERNAL.setNetwork(network.getSelfLink()), allowInternal);
    assertFirewallRuleMatch(LEONARDO_SSL.setNetwork(network.getSelfLink()), leonardoSsl);
  }

  private void assertFirewallRuleMatch(Firewall expected, Firewall actual) {
    assertEquals(expected.getAllowed(), actual.getAllowed());
    assertEquals(expected.getDescription(), actual.getDescription());
    assertEquals(expected.getDirection(), actual.getDirection());
    assertEquals(expected.getPriority(), actual.getPriority());
    assertEquals(expected.getNetwork(), actual.getNetwork());
  }

  private void assertSubnetsExist(Project project, NetworkMonitoring networkMonitoring)
      throws Exception {
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    for (Map.Entry<String, String> entry : REGION_TO_IP_RANGE.entrySet()) {
      String region = entry.getKey();
      Subnetwork subnetwork =
          computeCow.subnetworks().get(project.getProjectId(), region, SUBNETWORK_NAME).execute();
      assertEquals(network.getSelfLink(), subnetwork.getNetwork());
      assertEquals(entry.getValue(), subnetwork.getIpCidrRange());
      assertEquals(
          networkMonitoring.equals(NetworkMonitoring.ENABLED), subnetwork.getEnableFlowLogs());
      assertEquals(
          networkMonitoring.equals(NetworkMonitoring.ENABLED),
          subnetwork.getPrivateIpGoogleAccess());
      if (networkMonitoring.equals(NetworkMonitoring.ENABLED)) {
        assertEquals(LOG_CONFIG, subnetwork.getLogConfig());
      }
    }
  }

  private void assertRouteExists(Project project) throws Exception {
    String projectId = project.getProjectId();
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    Route route = computeCow.routes().get(projectId, ROUTE_NAME).execute();
    assertEquals(DESTINATION_RANGE, route.getDestRange());
    assertEquals(
        "https://www.googleapis.com/compute/v1/projects/" + projectId + DEFAULT_GATEWAY,
        route.getNextHopGateway());
    assertEquals(network.getSelfLink(), route.getNetwork());
  }

  private void assertRouteNotExists(Project project) throws Exception {
    assertFalse(
        resourceExists(
            () -> computeCow.routes().get(project.getProjectId(), ROUTE_NAME).execute(), 404));
  }

  private void assertDnsExists(Project project) throws Exception {
    String projectId = project.getProjectId();

    ManagedZone managedZone = dnsCow.managedZones().get(projectId, MANAGED_ZONE_NAME).execute();
    Map<String, ResourceRecordSet> resourceRecordSets =
        dnsCow.resourceRecordSets().list(project.getProjectId(), MANAGED_ZONE_NAME).execute()
            .getRrsets().stream()
            .collect(Collectors.toMap(ResourceRecordSet::getType, r -> r));
    ResourceRecordSet aRecordSet = resourceRecordSets.get(A_RECORD.getType());
    ResourceRecordSet cnameRecordSet = resourceRecordSets.get(CNAME_RECORD.getType());

    assertEquals(MANAGED_ZONE_TEMPLATE.getName(), managedZone.getName());
    assertEquals(MANAGED_ZONE_TEMPLATE.getVisibility(), managedZone.getVisibility());
    assertEquals(MANAGED_ZONE_TEMPLATE.getDescription(), managedZone.getDescription());
    assertResourceRecordSetMatch(A_RECORD, aRecordSet);
    assertResourceRecordSetMatch(CNAME_RECORD, cnameRecordSet);
  }

  private void assertDnsNotExists(Project project) throws Exception {
    assertFalse(
        resourceExists(
            () -> dnsCow.managedZones().get(project.getProjectId(), MANAGED_ZONE_NAME).execute(),
            404));
  }

  private void assertResourceRecordSetMatch(ResourceRecordSet expected, ResourceRecordSet actual) {
    assertEquals(expected.getName(), actual.getName());
    assertEquals(expected.getRrdatas(), actual.getRrdatas());
    assertEquals(expected.getTtl(), actual.getTtl());
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

  /**
   * Create a string matching the service name on {@link GoogleApiServiceusageV1Service#getName()},
   * e.g. projects/123/services/serviceusage.googleapis.com.
   */
  private static String serviceName(Project project, String apiId) {
    return String.format("projects/%d/services/%s", project.getProjectNumber(), apiId);
  }
}
