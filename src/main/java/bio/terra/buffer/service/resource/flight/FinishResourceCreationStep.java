package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.flight.StepUtils.markResourceReady;

import bio.terra.buffer.db.*;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

/**
 * The step after resource is successfully created, it updates resource entity to set
 * CloudResourceUid and state to READY.
 */
public class FinishResourceCreationStep implements Step {
  private final BufferDao bufferDao;

  public FinishResourceCreationStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    markResourceReady(bufferDao, flightContext);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
