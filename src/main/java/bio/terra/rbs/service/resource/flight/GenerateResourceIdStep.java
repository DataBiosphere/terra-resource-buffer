package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.db.*;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.util.UUID;

/** The step to create resourceId and put it in Stairway working map. */
public class GenerateResourceIdStep implements Step {
  public GenerateResourceIdStep() {}

  @Override
  public StepResult doStep(FlightContext flightContext) {
    flightContext
        .getWorkingMap()
        .put(FlightMapKeys.RESOURCE_ID, ResourceId.create(UUID.randomUUID()));

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
