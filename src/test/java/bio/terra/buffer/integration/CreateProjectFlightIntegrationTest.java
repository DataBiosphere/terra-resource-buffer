package bio.terra.buffer.integration;

import static bio.terra.buffer.integration.IntegrationUtils.IAM_BINDINGS;
import static bio.terra.buffer.integration.IntegrationUtils.StubSubmissionFlightFactory;
import static bio.terra.buffer.integration.IntegrationUtils.TEST_CONFIG_NAME;
import static bio.terra.buffer.integration.IntegrationUtils.blockUntilFlightComplete;
import static bio.terra.buffer.integration.IntegrationUtils.extractResourceIdFromFlightState;
import static bio.terra.buffer.integration.IntegrationUtils.newBasicGcpConfig;
import static bio.terra.buffer.integration.IntegrationUtils.newFullGcpConfig;
import static bio.terra.buffer.integration.IntegrationUtils.pollUntilResourcesMatch;
import static bio.terra.buffer.integration.IntegrationUtils.preparePool;
import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.buffer.service.resource.flight.CreateDnsZoneStep.MANAGED_ZONE_TEMPLATE;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.ALLOW_INTERNAL_RULE_NAME_FOR_DEFAULT;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.ALLOW_INTERNAL_RULE_NAME_FOR_NETWORK;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.LEONARDO_SSL_RULE_NAME_FOR_DEFAULT;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.LEONARDO_SSL_RULE_NAME_FOR_NETWORK;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.buildAllowInternalFirewallRule;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.buildLeonardoSslFirewallRule;
import static bio.terra.buffer.service.resource.flight.CreateProjectStep.CONFIG_NAME_LABEL_LEY;
import static bio.terra.buffer.service.resource.flight.CreateProjectStep.NETWORK_LABEL_KEY;
import static bio.terra.buffer.service.resource.flight.CreateProjectStep.SUB_NETWORK_LABEL_KEY;
import static bio.terra.buffer.service.resource.flight.CreateProjectStep.createValidLabelValue;
import static bio.terra.buffer.service.resource.flight.CreateResourceRecordSetStep.A_RECORD;
import static bio.terra.buffer.service.resource.flight.CreateResourceRecordSetStep.CNAME_RECORD;
import static bio.terra.buffer.service.resource.flight.CreateRouteStep.DEFAULT_GATEWAY;
import static bio.terra.buffer.service.resource.flight.CreateRouteStep.DESTINATION_RANGE;
import static bio.terra.buffer.service.resource.flight.CreateRouteStep.ROUTE_NAME;
import static bio.terra.buffer.service.resource.flight.CreateStorageLogBucketStep.STORAGE_LOGS_IDENTITY;
import static bio.terra.buffer.service.resource.flight.CreateSubnetsStep.LOG_CONFIG;
import static bio.terra.buffer.service.resource.flight.CreateSubnetsStep.REGION_TO_IP_RANGE;
import static bio.terra.buffer.service.resource.flight.DeleteDefaultFirewallRulesStep.DEFAULT_FIREWALL_NAMES;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.DEFAULT_NETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.MANAGED_ZONE_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.SUBNETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.projectIdToName;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.resourceExists;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseIntegrationTest;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.ComputeEngine;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.IamBinding;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.service.resource.FlightManager;
import bio.terra.buffer.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.buffer.service.resource.flight.AssertResourceCreatingStep;
import bio.terra.buffer.service.resource.flight.CreateProjectStep;
import bio.terra.buffer.service.resource.flight.ErrorStep;
import bio.terra.buffer.service.resource.flight.FinishResourceCreationStep;
import bio.terra.buffer.service.resource.flight.GenerateProjectIdStep;
import bio.terra.buffer.service.resource.flight.GoogleProjectCreationFlight;
import bio.terra.buffer.service.resource.flight.LatchStep;
import bio.terra.buffer.service.resource.flight.UndoCreatingDbEntityStep;
import bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator;
import bio.terra.buffer.service.stairway.StairwayComponent;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Route;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.dns.model.ManagedZone;
import com.google.api.services.dns.model.ResourceRecordSet;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.api.services.serviceusage.v1.model.GoogleApiServiceusageV1Service;
import com.google.cloud.Policy;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.StorageRoles;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CreateProjectFlightIntegrationTest extends BaseIntegrationTest {
  @Autowired BufferDao bufferDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudComputeCow computeCow;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired CloudBillingClientCow billingCow;
  @Autowired DnsCow dnsCow;
  @Autowired IamCow iamCow;
  @Autowired ServiceUsageCow serviceUsageCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;
  @Autowired ClientConfig clientConfig;
  @Autowired TransactionTemplate transactionTemplate;

  enum NetworkMonitoring {
    ENABLED,
    DISABLED
  }

  @Test
  public void testCreateGoogleProject_basicCreation() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool = preparePool(bufferDao, newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    Project project = assertProjectExists(resourceId);
    assertBillingIs(project, pool.resourceConfig().getGcpProjectConfig().getBillingAccount());
    assertEnableApisContains(project, pool.resourceConfig().getGcpProjectConfig().getEnabledApis());
    assertLogStorageBucketExists(project);
    assertNetworkExists(project);
    assertFirewallRulesExist(project);
    assertSubnetsExist(project, NetworkMonitoring.DISABLED);
    assertRouteNotExists(project);
    assertDnsNotExists(project);
    assertDefaultVpcNotExists(project);
    assertDefaultServiceAccountNotExists(project);
  }

  @Test
  public void testCreateGoogleProject_witIamBindings() throws Exception {
    // Basic GCP project with IAM Bindings
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool = preparePool(bufferDao, newBasicGcpConfig().iamBindings(IAM_BINDINGS));

    String flightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    Project project = assertProjectExists(resourceId);
    assertIamBindingsContains(project, IAM_BINDINGS);
  }

  @Test
  public void testCreateGoogleProject_enableNetworkMonitoring() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool =
        preparePool(
            bufferDao,
            newBasicGcpConfig()
                .network(
                    new bio.terra.buffer.generated.model.Network().enableNetworkMonitoring(true)));

    String flightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    Project project = assertProjectExists(resourceId);
    assertNetworkExists(project);
    assertSubnetsExist(project, NetworkMonitoring.ENABLED);
    assertRouteExists(project);
    assertDnsExists(project);
    assertDefaultVpcNotExists(project);
  }

  @Test
  public void testCreateGoogleProject_keepDefaultComputeEngineServiceAcct() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool =
        preparePool(
            bufferDao,
            newBasicGcpConfig().computeEngine(new ComputeEngine().keepDefaultServiceAcct(true)));

    String flightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    Project project = assertProjectExists(resourceId);
    assertDefaultServiceAccountExists(project);
  }

  @Test
  public void testCreateGoogleProject_keepDefaultNetwork() throws Exception {
    FlightManager manager =
        new FlightManager(
            bufferDao, flightSubmissionFactoryImpl, stairwayComponent, transactionTemplate);
    Pool pool =
        preparePool(
            bufferDao,
            newBasicGcpConfig()
                .network(new bio.terra.buffer.generated.model.Network().keepDefaultNetwork(true)));

    String flightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    Project project = assertProjectExists(resourceId);
    assertNetworkExists(project);
    assertFirewallRulesExist(project);
    assertDefaultVpcExists(project);
    assertFirewallRulesExistForDefaultVpc(project);
  }

  @Test
  public void testCreateGoogleProject_multipleSteps() throws Exception {
    // Verify flight is able to finish with multiple same steps exists.
    FlightManager manager =
        new FlightManager(
            bufferDao,
            new StubSubmissionFlightFactory(MultiInstanceStepFlight.class),
            stairwayComponent,
            transactionTemplate);
    Pool pool = preparePool(bufferDao, newFullGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    ResourceId resourceId =
        extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    Project project = assertProjectExists(resourceId);
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
            bufferDao,
            new StubSubmissionFlightFactory(ErrorCreateProjectFlight.class),
            stairwayComponent,
            transactionTemplate);
    Pool pool = preparePool(bufferDao, newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    // Resource is created in db
    Resource resource =
        pollUntilResourcesMatch(bufferDao, pool.id(), ResourceState.CREATING, 1).get(0);

    LatchStep.releaseLatch();
    extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    // Resource is deleted.
    assertFalse(bufferDao.retrieveResource(resource.id()).isPresent());
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(flightId).getFlightStatus());
  }

  @Test
  public void errorCreateProject_noRollbackAfterResourceReady() throws Exception {
    // Verify project and db entity won't get deleted if resource id READY, even the flight fails.
    FlightManager manager =
        new FlightManager(
            bufferDao,
            new StubSubmissionFlightFactory(ErrorAfterCreateResourceFlight.class),
            stairwayComponent,
            transactionTemplate);

    Pool pool = preparePool(bufferDao, newBasicGcpConfig());
    String flightId = manager.submitCreationFlight(pool).get();
    extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));

    Resource resource =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, 1).get(0);
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

  @Test
  public void testCreateValidLabel() {
    assertEquals("test-config-name", createValidLabelValue("TEST-CONFIG-NAME"));
    assertEquals("test--config--name--", createValidLabelValue("test@@Config@@Name@@"));
    assertEquals(
        "1234567890"
            + "1234567890"
            + "1234567890"
            + "1234567890"
            + "1234567890"
            + "1234567890"
            + "123",
        createValidLabelValue(
            "1234567890"
                + "1234567890"
                + "1234567890"
                + "1234567890"
                + "1234567890"
                + "1234567890"
                + "1234567890"
                + "1234567890"));
  }

  @Test
  public void testCreateGoogleProject_errorWhenResourceStateChange() throws Exception {
    LatchStep.startNewLatch();
    FlightManager manager =
        new FlightManager(
            bufferDao,
            new StubSubmissionFlightFactory(LatchBeforeAssertResourceStep.class),
            stairwayComponent,
            transactionTemplate);
    Pool pool = preparePool(bufferDao, newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    // Resource is created in db
    Resource resource =
        pollUntilResourcesMatch(bufferDao, pool.id(), ResourceState.CREATING, 1).get(0);

    // Delete the resource from DB.
    assertTrue(bufferDao.deleteResource(resource.id()));

    // Release the latch, and resume the flight, assert flight failed.
    LatchStep.releaseLatch();
    extractResourceIdFromFlightState(blockUntilFlightComplete(stairwayComponent, flightId));
    // Resource is deleted.
    assertFalse(bufferDao.retrieveResource(resource.id()).isPresent());
    assertEquals(
        FlightStatus.ERROR, stairwayComponent.get().getFlightState(flightId).getFlightStatus());
  }

  /** A {@link Flight} that will fail to create Google Project. */
  public static class ErrorCreateProjectFlight extends Flight {
    public ErrorCreateProjectFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      BufferDao bufferDao = ((ApplicationContext) applicationContext).getBean(BufferDao.class);
      CloudResourceManagerCow rmCow =
          ((ApplicationContext) applicationContext).getBean(CloudResourceManagerCow.class);
      GcpProjectConfig gcpProjectConfig =
          inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();
      GcpProjectIdGenerator idGenerator =
          ((ApplicationContext) applicationContext).getBean(GcpProjectIdGenerator.class);
      addStep(new LatchStep());
      addStep(new UndoCreatingDbEntityStep(bufferDao));
      addStep(new GenerateProjectIdStep(gcpProjectConfig, idGenerator));
      addStep(new ErrorCreateProjectStep(rmCow, gcpProjectConfig));
      addStep(new FinishResourceCreationStep(bufferDao));
    }
  }

  /** A {@link Flight} that has a {@link LatchStep} before {@link AssertResourceCreatingStep}. */
  public static class LatchBeforeAssertResourceStep extends GoogleProjectCreationFlight {
    public LatchBeforeAssertResourceStep(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step, RetryRule retryRule) {
      if (step instanceof AssertResourceCreatingStep) {
        addStep(new LatchStep());
      }
      super.addStep(step, retryRule);
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
     * Steps that doesn't need to handle "retry after succeed" scenario, if duplicates happens, the
     * flight will fail instead of success. Those steps are:
     *
     * <ul>
     *   <li>CreateResourceDbEntityStep: No long waiting operations inside, it's will not trigger
     *       "retry after succeed" cases.
     *   <li>CreateProjectStep: We want to fail the flight to avoid project id collision.
     * </ul>
     */
    private static final List<Class<? extends Step>> SKIP_DUP_CHECK_STEP_CLAZZ =
        ImmutableList.of(CreateProjectStep.class);

    public MultiInstanceStepFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step, RetryRule retryRule) {
      super.addStep(step, retryRule);

      if (!SKIP_DUP_CHECK_STEP_CLAZZ.stream().anyMatch(clazz -> clazz.isInstance(step))) {
        super.addStep(step, retryRule);
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
    Resource resource = bufferDao.retrieveResource(resourceId).get();
    Project project =
        rmCow
            .projects()
            .get(resource.cloudResourceUid().getGoogleProjectUid().getProjectId())
            .execute();
    assertEquals("ACTIVE", project.getLifecycleState());
    assertEquals(
        ImmutableMap.of(
            NETWORK_LABEL_KEY,
            NETWORK_NAME,
            SUB_NETWORK_LABEL_KEY,
            SUBNETWORK_NAME,
            CONFIG_NAME_LABEL_LEY,
            TEST_CONFIG_NAME),
        project.getLabels());
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
        serviceUsageCow
            .services()
            .list(projectIdToName(project.getProjectId()))
            .setFilter("state:ENABLED")
            .execute()
            .getServices()
            .stream()
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
        rmCow
            .projects()
            .getIamPolicy(project.getProjectId(), new GetIamPolicyRequest())
            .execute()
            .getBindings()
            .stream()
            .collect(Collectors.toMap(Binding::getRole, Binding::getMembers));

    for (IamBinding iamBinding : iamBindings) {
      assertThat(
          new ArrayList<>(allBindings.get(iamBinding.getRole())),
          Matchers.hasItems(iamBinding.getMembers().toArray()));
    }
  }

  private void assertLogStorageBucketExists(Project project) throws Exception {
    String projectId = project.getProjectId();
    StorageCow storageCow =
        new StorageCow(clientConfig, StorageOptions.newBuilder().setProjectId(projectId).build());
    String bucketName = "storage-logs-" + projectId;
    BucketInfo bucketInfo = storageCow.get(bucketName).getBucketInfo();
    // There might be multiple ACLs as we didn't remove the default ACLs. Only need to verify the
    // one we just add exists.
    Policy policy = storageCow.getIamPolicy(bucketName);
    assertTrue(
        policy
            .getBindings()
            .get(StorageRoles.legacyBucketWriter())
            .contains(STORAGE_LOGS_IDENTITY));
  }

  private void assertNetworkExists(Project project) throws Exception {
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    assertFalse(network.getAutoCreateSubnetworks());
  }

  private void assertFirewallRulesExist(Project project) throws Exception {
    String projectId = project.getProjectId();
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    Firewall allowInternal =
        computeCow.firewalls().get(projectId, ALLOW_INTERNAL_RULE_NAME_FOR_NETWORK).execute();
    Firewall leonardoSsl =
        computeCow.firewalls().get(projectId, LEONARDO_SSL_RULE_NAME_FOR_NETWORK).execute();

    Firewall allowInternalExpected =
        buildAllowInternalFirewallRule(network, ALLOW_INTERNAL_RULE_NAME_FOR_NETWORK);
    Firewall leonardoSslExpected =
        buildLeonardoSslFirewallRule(network, LEONARDO_SSL_RULE_NAME_FOR_NETWORK);

    assertFirewallRuleMatch(allowInternalExpected, allowInternal);
    assertFirewallRuleMatch(leonardoSslExpected, leonardoSsl);
  }

  private void assertFirewallRulesExistForDefaultVpc(Project project) throws Exception {
    String projectId = project.getProjectId();
    Network network =
        computeCow.networks().get(project.getProjectId(), DEFAULT_NETWORK_NAME).execute();
    Firewall allowInternal =
        computeCow.firewalls().get(projectId, ALLOW_INTERNAL_RULE_NAME_FOR_DEFAULT).execute();
    Firewall leonardoSsl =
        computeCow.firewalls().get(projectId, LEONARDO_SSL_RULE_NAME_FOR_DEFAULT).execute();

    Firewall allowInternalExpected =
        buildAllowInternalFirewallRule(network, ALLOW_INTERNAL_RULE_NAME_FOR_DEFAULT);
    Firewall leonardoSslExpected =
        buildLeonardoSslFirewallRule(network, LEONARDO_SSL_RULE_NAME_FOR_DEFAULT);

    assertFirewallRuleMatch(allowInternalExpected, allowInternal);
    assertFirewallRuleMatch(leonardoSslExpected, leonardoSsl);
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
        dnsCow
            .resourceRecordSets()
            .list(project.getProjectId(), MANAGED_ZONE_NAME)
            .execute()
            .getRrsets()
            .stream()
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

  private void assertDefaultVpcExists(Project project) throws Exception {
    // Check default VPC network exists
    Network network =
        computeCow.networks().get(project.getProjectId(), DEFAULT_NETWORK_NAME).execute();
    assertTrue(network.getAutoCreateSubnetworks());

    // And that the default firewall rules do not.
    for (String firewall : DEFAULT_FIREWALL_NAMES) {
      assertFalse(
          resourceExists(
              () -> computeCow.firewalls().get(project.getProjectId(), firewall).execute(), 404));
    }
  }

  private void assertDefaultVpcNotExists(Project project) throws Exception {
    for (String firewall : DEFAULT_FIREWALL_NAMES) {
      assertFalse(
          resourceExists(
              () -> computeCow.firewalls().get(project.getProjectId(), firewall).execute(), 404));
    }
    assertFalse(
        resourceExists(
            () -> computeCow.networks().get(project.getProjectId(), DEFAULT_NETWORK_NAME).execute(),
            404));
  }

  private void assertDefaultServiceAccountExists(Project project) throws Exception {
    List<ServiceAccount> serviceAccounts =
        iamCow
            .projects()
            .serviceAccounts()
            .list("projects/" + project.getProjectId())
            .execute()
            .getAccounts();
    assertEquals(1, serviceAccounts.size());
    assertEquals("Compute Engine default service account", serviceAccounts.get(0).getDisplayName());
    assertThat(
        serviceAccounts.get(0).getEmail(),
        Matchers.containsString("-compute@developer.gserviceaccount.com"));
  }

  private void assertDefaultServiceAccountNotExists(Project project) throws Exception {
    assertNull(
        iamCow
            .projects()
            .serviceAccounts()
            .list("projects/" + project.getProjectId())
            .execute()
            .getAccounts());
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
