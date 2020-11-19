package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.db.BufferDao;
import bio.terra.stairway.*;
import java.util.Optional;

/** .Step to set resource state to DELETING. */
public class InitialResourceDeletionStep implements Step {
  private final BufferDao bufferDao;

  public InitialResourceDeletionStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    ResourceId resourceId = ResourceId.retrieve(flightContext.getInputParameters());
    Optional<Resource> resource = bufferDao.retrieveResource(resourceId);
    if (resource.isPresent() && resource.get().state().equals(ResourceState.READY)) {
      // Only update READY state resource.
      bufferDao.updateResourceAsDeleting(resourceId);
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
