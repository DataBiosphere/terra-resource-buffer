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
      // CREATING, HANDED_OUT, etc.).
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Rolling back resource state READY requires more complicated workflow but we think this is
    // rare and unnecessary.
    // We would just fail the flight.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
