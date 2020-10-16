package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_READY;
import static bio.terra.rbs.service.resource.flight.StepUtils.markResourceReady;

import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

/**
 * The step after resource is successfully created, it updates resource entity to set
 * CloudResourceUid and state to READY.
 */
public class FinishResourceCreationStep implements Step {
  private final RbsDao rbsDao;

  public FinishResourceCreationStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    markResourceReady(rbsDao, flightContext);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
