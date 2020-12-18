package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.flight.StepUtils.isResourceReady;

import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.db.BufferDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

/**
 * Initial resource creation step. Now we create CREATING resource before the flight, but we need to
 * delete it if flight fails.
 */
public class InitialResourceCreationStep implements Step {
  private final BufferDao bufferDao;

  public InitialResourceCreationStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    if (isResourceReady(flightContext)) {
      return StepResult.getStepResultSuccess();
    }
    // Just delete the resource entity if creation not succeed. There is no need to keep this
    // record.
    bufferDao.deleteResource(ResourceId.retrieve(flightContext.getInputParameters()));
    return StepResult.getStepResultSuccess();
  }
}
