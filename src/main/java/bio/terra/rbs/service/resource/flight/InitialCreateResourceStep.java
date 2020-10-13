package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.db.*;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.time.Instant;
import java.util.UUID;

/**
 * The initial step to create resource. It creates a entity in resource db table with CREATING
 * state.
 */
public class InitialCreateResourceStep implements Step {
  private final RbsDao rbsDao;

  public InitialCreateResourceStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    flightContext.getWorkingMap();
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    ResourceId resourceId = ResourceId.create(UUID.randomUUID());
    workingMap.put(FlightMapKeys.RESOURCE_ID, resourceId);

    rbsDao.createResource(
        Resource.builder()
            .id(ResourceId.create(UUID.randomUUID()))
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
