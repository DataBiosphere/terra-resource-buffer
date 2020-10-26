package bio.terra.rbs.integration;

import static bio.terra.rbs.integration.IntegrationUtils.pollUntilResourcesMatch;
import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.rbs.service.resource.flight.CreateRouteStep.*;
import static bio.terra.rbs.service.resource.flight.CreateSubnetsStep.*;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.NETWORK_NAME;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.projectIdToName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.rbs.common.*;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.IamBinding;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.rbs.service.resource.FlightManager;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.rbs.service.resource.FlightSubmissionFactory;
import bio.terra.rbs.service.resource.FlightSubmissionFactoryImpl;
import bio.terra.rbs.service.resource.flight.*;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Route;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.serviceusage.v1.model.GoogleApiServiceusageV1Service;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
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
  /** The billing account to use in test. */
  private static final String BILLING_ACCOUNT_NAME = "01A82E-CA8A14-367457";

  @Autowired RbsDao rbsDao;
  @Autowired StairwayComponent stairwayComponent;
  @Autowired CloudComputeCow computeCow;
  @Autowired CloudResourceManagerCow rmCow;
  @Autowired CloudBillingClientCow billingCow;
  @Autowired ServiceUsageCow serviceUsageCow;
  @Autowired FlightSubmissionFactoryImpl flightSubmissionFactoryImpl;

  enum NetworkMonitoring {
    ENABLED,
    DISABLED
  }

  @Test
  public void testCreateGoogleProject_basicCreation() throws Exception {
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool = preparePool(newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);
    Project project = assertProjectExists(pool);
    assertBillingIs(project, pool.resourceConfig().getGcpProjectConfig().getBillingAccount());
    assertEnableApisContains(project, pool.resourceConfig().getGcpProjectConfig().getEnabledApis());
    assertNetworkExists(project);
    assertSubnetsExist(project, NetworkMonitoring.DISABLED);
  }

  @Test
  public void testCreateGoogleProject_witIamBindings() throws Exception {
    // The groups used to test IAM policy sets up on a group. It doesn't matter what the users are
    // for the purpose of this test. They just need to exist for Google.
    // These groups were manually created for Broad development via the BITs service portal.
    String testGroupName = "terra-rbs-test@broadinstitute.org";
    String testGroupViewerName = "terra-rbs-viewer-test@broadinstitute.org";

    List<IamBinding> iamBindings =
        Arrays.asList(
            new IamBinding().role("roles/editor").addMembersItem("group:" + testGroupName),
            new IamBinding().role("roles/viewer").addMembersItem("group:" + testGroupViewerName));

    // Basic GCP project with IAM Bindings
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool = preparePool(newBasicGcpConfig().iamBindings(iamBindings));

    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);
    Project project = assertProjectExists(pool);
    assertIamBindingsContains(project, iamBindings);
  }

  @Test
  public void testCreateGoogleProject_enableNetworkMonitoring() throws Exception {
    FlightManager manager = new FlightManager(flightSubmissionFactoryImpl, stairwayComponent);
    Pool pool =
        preparePool(
            newBasicGcpConfig()
                .network(
                    new bio.terra.rbs.generated.model.Network().enableNetworkMonitoring(true)));

    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);
    Project project = assertProjectExists(pool);
    assertNetworkExists(project);
    assertSubnetsExist(project, NetworkMonitoring.ENABLED);
    assertRouteExist(project);
  }

  @Test
  public void testCreateGoogleProject_errorDuringProjectCreation() throws Exception {
    // Verify flight is able to successfully rollback when project fails to create and doesn't
    // exist.
    LatchStep.startNewLatch();
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(ErrorCreateProjectFlight.class), stairwayComponent);
    Pool pool = preparePool(newBasicGcpConfig());

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
  public void testCreateGoogleProject_multipleNetworkCreation() throws Exception {
    // Verify flight is able to finish successfully when network exists
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(MultiNetworkStepFlight.class), stairwayComponent);
    Pool pool = preparePool(newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);
    Project project = assertProjectExists(pool);
    assertEnableApisContains(project, pool.resourceConfig().getGcpProjectConfig().getEnabledApis());
    assertNetworkExists(project);
  }

  @Test
  public void testCreateGoogleProject_multipleSubnetsCreation() throws Exception {
    // Verify flight is able to finish successfully when subnets already exists/
    // this scenario may arise when the step partially fails and ends up in a state where some
    // subnets need to be recreated and some are getting created the first time.
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(MultiSubnetsStepFlight.class), stairwayComponent);
    Pool pool = preparePool(newBasicGcpConfig());

    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);
    Project project = assertProjectExists(pool);
    assertNetworkExists(project);
    assertSubnetsExist(project, NetworkMonitoring.DISABLED);
  }

  @Test
  public void errorCreateProject_noRollbackAfterResourceReady() throws Exception {
    // Verify project and db entity won't get deleted if resource id READY, even the flight fails.
    FlightManager manager =
        new FlightManager(
            new StubSubmissionFlightFactory(ErrorAfterCreateResourceFlight.class),
            stairwayComponent);

    Pool pool = preparePool(newBasicGcpConfig());
    String flightId = manager.submitCreationFlight(pool).get();
    blockUntilFlightComplete(flightId);

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

  /** A {@link Flight} that has multiple network creation steps. */
  public static class MultiNetworkStepFlight extends GoogleProjectCreationFlight {
    public MultiNetworkStepFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step) {
      super.addStep(step);
      if (step instanceof CreateNetworkStep) {
        // Create a duplicate entry for any CreateNetworkStep in the original flight path.
        super.addStep(step);
      }
    }
  }

  /**
   * A {@link Flight} that has multiple subnets creation steps. So a flight will try to create all
   * Subnets twice and still success.
   */
  public static class MultiSubnetsStepFlight extends GoogleProjectCreationFlight {

    public MultiSubnetsStepFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step) {
      super.addStep(step);
      if (step instanceof CreateSubnetsStep) {
        // Create a duplicate entry for any CreateSubnetsStep in the original flight path.
        super.addStep(step);
      }
    }
  }

  /** A {@link Flight} that has multiple route creation steps. */
  public static class MultiRouteStepFlight extends GoogleProjectCreationFlight {
    public MultiRouteStepFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected void addStep(Step step) {
      super.addStep(step);
      if (step instanceof CreateRouteStep) {
        // Create a duplicate entry for any CreateRouteStep in the original flight path.
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

  /** Prepares a Pool with {@link GcpProjectConfig}. */
  private Pool preparePool(GcpProjectConfig gcpProjectConfig) {
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(
                new ResourceConfig().configName("configName").gcpProjectConfig(gcpProjectConfig))
            .status(PoolStatus.ACTIVE)
            .creation(Instant.now())
            .build();
    rbsDao.createPools(ImmutableList.of(pool));
    assertTrue(rbsDao.retrieveResources(pool.id(), ResourceState.CREATING, 1).isEmpty());
    assertTrue(rbsDao.retrieveResources(pool.id(), ResourceState.READY, 1).isEmpty());
    return pool;
  }

  /** Create a Basic {@link ResourceConfig}. */
  private static GcpProjectConfig newBasicGcpConfig() {
    return new GcpProjectConfig()
        .projectIDPrefix("prefix")
        .parentFolderId(FOLDER_ID)
        .billingAccount(BILLING_ACCOUNT_NAME)
        .addEnabledApisItem("compute.googleapis.com");
  }

  private Project assertProjectExists(Pool pool) throws Exception {
    Resource resource = rbsDao.retrieveResources(pool.id(), ResourceState.READY, 1).get(0);
    assertEquals(pool.id(), resource.poolId());
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

  private void assertRouteExist(Project project) throws Exception {
    String projectId = project.getProjectId();
    Network network = computeCow.networks().get(project.getProjectId(), NETWORK_NAME).execute();
    Route route = computeCow.routes().get(projectId, ROUTE_NAME).execute();
    assertEquals(DESTINATION_RANGE, route.getDestRange());
    assertEquals(
        "https://www.googleapis.com/compute/v1/projects/" + projectId + DEFAULT_GATEWAY,
        route.getNextHopGateway());
    assertEquals(network.getSelfLink(), route.getNetwork());
  }

  /** A {@link FlightSubmissionFactory} used in test. */
  public static class StubSubmissionFlightFactory implements FlightSubmissionFactory {
    public final Class<? extends Flight> flightClass;

    public StubSubmissionFlightFactory(Class<? extends Flight> flightClass) {
      this.flightClass = flightClass;
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

  /**
   * Create a string matching the service name on {@link GoogleApiServiceusageV1Service#getName()},
   * e.g. projects/123/services/serviceusage.googleapis.com.
   */
  private static String serviceName(Project project, String apiId) {
    return String.format("projects/%d/services/%s", project.getProjectNumber(), apiId);
  }
}
