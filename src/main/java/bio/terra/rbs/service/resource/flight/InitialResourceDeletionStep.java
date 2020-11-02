package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.common.ResourceState;
import bio.terra.rbs.db.RbsDao;
import bio.terra.stairway.*;
import java.util.Optional;

/** .Step to set resource state to DELETING. */
public class InitialResourceDeletionStep implements Step {
  private final RbsDao rbsDao;

  public InitialResourceDeletionStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    ResourceId resourceId = ResourceId.retrieve(flightContext.getInputParameters());
    Optional<Resource> resource = rbsDao.retrieveResource(resourceId);
    if (resource.isPresent() && resource.get().state().equals(ResourceState.READY)) {
      // Only update READY state resource.
      rbsDao.updateResourceAsDeleting(resourceId);
    } else if (!resource.isPresent() || !resource.get().state().equals(ResourceState.DELETING)) {
      // Fail the flight if resource is not found or resource state is not DELETING(DELETED,
      // CREATING, HANDED_OUT).
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We can not undo a deletion
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
