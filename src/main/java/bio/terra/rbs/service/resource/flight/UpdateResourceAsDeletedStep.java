package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.db.RbsDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.time.Instant;

/** The step after resource is successfully deleted, it updates resource state to DELETED. */
public class UpdateResourceAsDeletedStep implements Step {
  private final RbsDao rbsDao;

  public UpdateResourceAsDeletedStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    ResourceId resourceId = ResourceId.retrieve(flightContext.getInputParameters());
    rbsDao.updateResourceAsDeleted(resourceId, Instant.now());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Nothing to revert, the initial deletion step will set resource state back to READY.
    return StepResult.getStepResultSuccess();
  }
}
