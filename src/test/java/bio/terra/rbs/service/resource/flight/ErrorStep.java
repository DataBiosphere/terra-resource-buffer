package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;

/** An error step with successfully undo. */
public class ErrorStep implements Step {
  @Override
  public StepResult doStep(FlightContext flightContext) {
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
