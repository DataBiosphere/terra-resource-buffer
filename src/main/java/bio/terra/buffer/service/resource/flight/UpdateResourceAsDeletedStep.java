package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.db.BufferDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.time.Instant;

/** The step after resource is successfully deleted, it updates resource state to DELETED. */
public class UpdateResourceAsDeletedStep implements Step {
  private final BufferDao bufferDao;

  public UpdateResourceAsDeletedStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    ResourceId resourceId = ResourceId.retrieve(flightContext.getInputParameters());
    bufferDao.updateResourceAsDeleted(resourceId, Instant.now());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Nothing to revert, the initial deletion step will set resource state back to READY.
    return StepResult.getStepResultSuccess();
  }
}
