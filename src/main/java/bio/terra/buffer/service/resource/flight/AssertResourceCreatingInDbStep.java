package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.db.BufferDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

import static bio.terra.buffer.service.resource.flight.StepUtils.isResourceReady;

/**
 * A step with undo method to delete the entity from resource table. We create a CREATING state
 * resource and submit it to Stairway. If the flight fail, we need to delete the CREATING entity
 * from resource table since the resource is not CREATING anymore.
 */
public class AssertResourceCreatingInDbStep implements Step {
  private final BufferDao bufferDao;

  public AssertResourceCreatingInDbStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    // Do nothing. We just use this step's undo method.
    FlightMap workingMap = flightContext.getWorkingMap();
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
