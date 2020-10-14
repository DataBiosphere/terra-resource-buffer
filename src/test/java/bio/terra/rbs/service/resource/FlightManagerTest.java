package bio.terra.rbs.service.resource;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.rbs.service.resource.flight.CreateResourceDbEntityStep;
import bio.terra.rbs.service.resource.flight.GenerateResourceIdStep;
import bio.terra.rbs.service.resource.flight.LatchStep;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Tests in this file not cover real cloud resources creation/deletion. Full successful flights
 * tests is covered in integration test.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class FlightManagerTest extends BaseUnitTest {
  @Autowired StairwayComponent stairwayComponent;

  @Autowired RbsDao rbsDao;

  /**
   * Test inserted resource is deleted from DB if the following steps fail. Note that this doesn't
   * cover the case RBS/Stairway crashes during the flight.
   */
  @Test
  public void undoCreateResourceEntityStep_shouldDeleteDbRecord() throws Exception {
    LatchStep.startNewLatch();
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

    FlightManager flightManager =
        new FlightManager(new LatchAfterResourceEntityCreateFlightFactory(), stairwayComponent);
    String flightId = flightManager.submitCreationFlight(pool).get();
    Resource resource =
        pollUntilResourceStateExists(ResourceState.CREATING, 1, Duration.ofSeconds(1), 5).get(0);
    assertEquals(poolId, resource.poolId());

    LatchStep.releaseLatch();
    blockUntilFlightComplete(flightId);
    assertFalse(rbsDao.retrieveResource(resource.id()).isPresent());
  }

  private List<Resource> pollUntilResourceStateExists(
      ResourceState state, int expectNum, Duration period, int maxNumPolls) throws Exception {
    int numPolls = 0;
    while (numPolls < maxNumPolls) {
      TimeUnit.MILLISECONDS.sleep(period.toMillis());
      List<Resource> resources = rbsDao.retrieveResources(state, 1);
      if (resources.size() == expectNum) {
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

  /** A {@link Flight} that latches after inserting a CREATING entity in Resource table. */
  public static class LatchAfterResourceEntityCreateFlight extends Flight {
    public LatchAfterResourceEntityCreateFlight(
        FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      RbsDao rbsDao = ((ApplicationContext) applicationContext).getBean(RbsDao.class);
      addStep(new GenerateResourceIdStep());
      addStep(new CreateResourceDbEntityStep(rbsDao));
      addStep(new LatchStep());
      addStep(new ErrorStep());
    }
  }

  /** An error step with successfully undo. */
  public static class ErrorStep implements Step {
    @Override
    public StepResult doStep(FlightContext flightContext) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      return StepResult.getStepResultSuccess();
    }
  }

  /** A {@link FlightFactory} always returns {@link LatchAfterResourceEntityCreateFlight}. */
  public static class LatchAfterResourceEntityCreateFlightFactory implements FlightFactory {
    @Override
    public Class<? extends Flight> getCreationFlightClass(ResourceType type) {
      return LatchAfterResourceEntityCreateFlight.class;
    }

    @Override
    public Class<? extends Flight> getDeletionFlightClass(ResourceType type) {
      return LatchAfterResourceEntityCreateFlight.class;
    }
  }
}
