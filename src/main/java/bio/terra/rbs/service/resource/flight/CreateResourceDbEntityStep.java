package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.db.*;
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
    System.out.println("~~~~~~~~~~~~~~CreateResourceDbEntityStep undo");
    // Just delete the resource entity if creation not succeed. There is no need to keep this
    // record.
    rbsDao.deleteResource(ResourceId.retrieve(flightContext.getWorkingMap()));
    return StepResult.getStepResultSuccess();
  }
}
