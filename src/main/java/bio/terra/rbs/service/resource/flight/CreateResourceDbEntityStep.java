package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.db.*;
import bio.terra.rbs.service.resource.FlightMapKeys;
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
  private final RbsDao rbsDao;

  public CreateResourceDbEntityStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    ResourceId resourceId =
        flightContext.getWorkingMap().get(FlightMapKeys.RESOURCE_ID, ResourceId.class);

    rbsDao.createResource(
        Resource.builder()
            .id(resourceId)
            .poolId(inputMap.get(FlightMapKeys.POOL_ID, PoolId.class))
            .creation(Instant.now())
            .state(ResourceState.CREATING)
            .build());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Just delete the resource entity if creation not succeed. There is no need to keep this
    // record.
    rbsDao.deleteResource(
        flightContext.getWorkingMap().get(FlightMapKeys.RESOURCE_ID, ResourceId.class));
    return StepResult.getStepResultSuccess();
  }
}
