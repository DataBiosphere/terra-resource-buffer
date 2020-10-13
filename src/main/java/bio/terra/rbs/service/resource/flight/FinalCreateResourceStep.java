package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

/**
 * The initial step to create resource. It creates a entity in resource db table with CREATING
 * state.
 */
public class FinalCreateResourceStep implements Step {
  private final RbsDao rbsDao;

  public FinalCreateResourceStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();

    rbsDao.updateResourceAfterCreation(
        workingMap.get(FlightMapKeys.RESOURCE_ID, ResourceId.class),
        workingMap.get(FlightMapKeys.CLOUD_RESOURCE_UID, CloudResourceUid.class));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Nothing need to do.
    return StepResult.getStepResultSuccess();
  }
}
