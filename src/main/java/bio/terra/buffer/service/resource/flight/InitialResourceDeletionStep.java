package bio.terra.buffer.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;

/**
 * Initial resource deletion step. Now we mark resource as DELETING before the flight, but we need
 * to add extra step to handle the rollback, i.e. return STEP_RESULT_FAILURE_FATAL when rollback.
 */
public class InitialResourceDeletionStep implements Step {
  public InitialResourceDeletionStep() {}

  @Override
  public StepResult doStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Rolling back resource state READY requires more complicated workflow but we think this is
    // rare and unnecessary. We would just fail the flight.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
