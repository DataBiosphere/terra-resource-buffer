package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.common.PoolId;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.common.ResourceState;
import bio.terra.rbs.db.RbsDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

import java.time.Instant;

import static bio.terra.rbs.service.resource.flight.StepUtils.isResourceReady;

/** First step to deleting resources, i.e, update resource state to DELETING. */
public class InitialResourceDeletionStep implements Step {
  private final RbsDao rbsDao;

  public InitialResourceDeletionStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    ResourceId resourceId = ResourceId.retrieve(flightContext.getWorkingMap());

    rbsDao.createResource(
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
    rbsDao.deleteResource(ResourceId.retrieve(flightContext.getWorkingMap()));
    return StepResult.getStepResultSuccess();
  }
}
