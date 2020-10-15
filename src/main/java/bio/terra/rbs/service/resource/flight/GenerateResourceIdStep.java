package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.common.ResourceId;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.util.UUID;

/** The step to create resourceId and put it in Stairway working map. */
public class GenerateResourceIdStep implements Step {
  public GenerateResourceIdStep() {}

  @Override
  public StepResult doStep(FlightContext flightContext) {
    ResourceId.create(UUID.randomUUID()).store(flightContext.getWorkingMap());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
