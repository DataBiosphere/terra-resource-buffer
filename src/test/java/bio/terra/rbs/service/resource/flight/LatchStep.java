package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.util.concurrent.CountDownLatch;

/**
 * A step to block until the latch is released to be used for testing.
 *
 * <p>This step relies on in-memory state and does not work across services or after a service
 * restart. It is only useful for testing.
 *
 * <p>Reference:
 * https://github.com/DataBiosphere/terra-resource-janitor/blob/master/src/test/java/bio/terra/janitor/service/cleanup/flight/LatchStep.java
 */
public class LatchStep implements Step {
  private static CountDownLatch latch = new CountDownLatch(0);

  /** Start a latch for testing. */
  public static void startNewLatch() {
    latch = new CountDownLatch(1);
  }

  /** Releases the latch. */
  public static void releaseLatch() {
    latch.countDown();
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    latch.await();
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
