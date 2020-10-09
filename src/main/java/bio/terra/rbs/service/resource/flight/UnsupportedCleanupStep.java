package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;

/** A fatal step for resource types that have not been implemented yet. */
public class UnsupportedCleanupStep implements Step {
  @Override
  public StepResult doStep(FlightContext flightContext) {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new UnsupportedOperationException("UnsupportedResourceTypeStep"));
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
