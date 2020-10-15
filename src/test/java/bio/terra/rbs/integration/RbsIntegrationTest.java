package bio.terra.rbs.integration;

import static bio.terra.rbs.service.pool.PoolConfigLoader.loadPoolConfig;
import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.common.BaseIntegrationTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.rbs.service.pool.PoolService;
import bio.terra.rbs.service.resource.FlightFactory;
import bio.terra.rbs.service.resource.FlightManager;
import bio.terra.rbs.service.resource.flight.*;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.TransactionStatus;

@AutoConfigureMockMvc
public class RbsIntegrationTest extends BaseIntegrationTest {
  @Autowired CloudResourceManagerCow rmCow;

  @Autowired RbsDao rbsDao;
  @Autowired PoolService poolService;
  @Autowired StairwayComponent stairwayComponent;

  TransactionStatus transactionStatus;
  FlightFactory flightFactory = new TestingFlightFactory();

  @Test
  public void testCreateGoogleProject() throws Exception {
    // The pool id in config file.
    PoolId poolId = PoolId.create("ws_test_v1");
    poolService.updateFromConfig(loadPoolConfig("test/config"), transactionStatus);

    List<Resource> resources =
        pollUntilResourceExists(ResourceState.READY, poolId, 2, Duration.ofSeconds(10), 10);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(resource.cloudResourceUid());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });

    // Upgrade the size from 2 to 5. Expect 3 more resources will be created.
    rbsDao.updatePoolsSize(ImmutableMap.of(poolId, 5));
    resources = pollUntilResourceExists(ResourceState.READY, poolId, 5, Duration.ofSeconds(10), 10);
    resources.forEach(
        resource -> {
          try {
            assertProjectMatch(resource.cloudResourceUid());
          } catch (Exception e) {
            fail("Error occurs when verifying GCP project creation", e);
          }
        });
  }

  @Test
  public void testCreateGoogleProject_errorDuringProjectCreation() throws Exception {
    // Verify flight is able to successfully rollback when project fails to create and doesn't
    // exist.
    TestingFlightFactory.setFlightClassToUse(ErrorCreateProjectFlight.class);
    LatchStep.startNewLatch();

    FlightManager manager = new FlightManager(flightFactory, stairwayComponent);
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(
                new ResourceConfig()
                    .configName("configName")
                    .gcpProjectConfig(new GcpProjectConfig().projectIDPrefix("prefix")))
            .status(PoolStatus.ACTIVE)
            .creation(Instant.now())
            .build();

    rbsDao.createPools(ImmutableList.of(pool));
    assertTrue(rbsDao.retrieveResources(ResourceState.CREATING, 1).isEmpty());
    String flightId = manager.submitCreationFlight(pool).get();
    // Resource is created in db
    Resource resource =
        pollUntilResourceExists(ResourceState.CREATING, poolId, 1, Duration.ofSeconds(10), 10)
            .get(0);

    LatchStep.releaseLatch();
    blockUntilFlightComplete(flightId);
    // Resource is deleted.
    assertFalse(rbsDao.retrieveResource(resource.id()).isPresent());
  }

  private List<Resource> pollUntilResourceExists(
      ResourceState state, PoolId poolId, int expectedResourceNum, Duration period, int maxNumPolls)
      throws Exception {
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      List<Resource> resources =
          rbsDao.retrieveResources(state, 10).stream()
              .filter(r -> r.poolId().equals(poolId))
              .collect(Collectors.toList());
      if (resources.size() == expectedResourceNum) {
        return resources;
      }
      ++numPolls;
    }
    throw new InterruptedException("Polling exceeded maxNumPolls");
  }

  private void blockUntilFlightComplete(String flightId)
      throws InterruptedException, DatabaseOperationException {
    Duration maxWait = Duration.ofSeconds(10);
    Duration waited = Duration.ZERO;
    while (waited.compareTo(maxWait) < 0) {
      if (!stairwayComponent.get().getFlightState(flightId).isActive()) {
        return;
      }
      int pollMs = 100;
      waited.plus(Duration.ofMillis(pollMs));
      TimeUnit.MILLISECONDS.sleep(pollMs);
    }
    throw new InterruptedException("Flight did not complete in time.");
  }

  private void assertProjectMatch(CloudResourceUid resourceUid) throws Exception {
    Project project =
        rmCow.projects().get(resourceUid.getGoogleProjectUid().getProjectId()).execute();
    assertEquals("ACTIVE", project.getLifecycleState());
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
      addStep(new ErrorCreateGoogleProjectStep(rmCow, gcpProjectConfig));
      addStep(new FinishResourceCreationStep(rbsDao));
    }
  }

  /** A {@link FlightFactory} used in test. */
  public static class TestingFlightFactory implements FlightFactory {
    public static Class<? extends Flight> flightClass;

    public static void setFlightClassToUse(Class<? extends Flight> clazz) {
      flightClass = clazz;
    }

    @Override
    public Class<? extends Flight> getCreationFlightClass(ResourceType type) {
      return flightClass;
    }

    @Override
    public Class<? extends Flight> getDeletionFlightClass(ResourceType type) {
      return flightClass;
    }
  }
}
