package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.flight.StepUtils.isResourceReady;

import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.db.*;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.time.Instant;

/**
 * Step to create resource record in DB. Depends on previous CreateResourceId step to generate
 * {@link ResourceId}
 */
public class CreateResourceDbEntityStep implements Step {
  private final BufferDao bufferDao;

  public CreateResourceDbEntityStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    ResourceId resourceId = ResourceId.retrieve(flightContext.getWorkingMap());

    bufferDao.createResource(
        Resource.builder()
            .id(resourceId)
            .poolId(PoolId.retrieve(inputMap))
            .creation(Instant.now())
            .state(ResourceState.CREATING)
            .build());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    if (isResourceReady(flightContext)) {
      return StepResult.getStepResultSuccess();
    }
    // Just delete the resource entity if creation not succeed. There is no need to keep this
    // record.
    bufferDao.deleteResource(ResourceId.retrieve(flightContext.getWorkingMap()));
    return StepResult.getStepResultSuccess();
  }
}
