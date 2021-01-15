package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.db.BufferDao;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks if resource exists and in DELETING state before the actual deletion. It may happen when
 * submitting flight success but updating DB fails.
 */
public class AssertResourceCreatingInDbStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(AssertResourceCreatingInDbStep.class);

  private final BufferDao bufferDao;

  public AssertResourceCreatingInDbStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    // Do nothing. We just use this step's undo method.
    Optional<Resource> resource =
        bufferDao.retrieveResource(ResourceId.retrieve(flightContext.getInputParameters()));
    if (resource.isPresent() && resource.get().state().equals(ResourceState.CREATING)) {
      return StepResult.getStepResultSuccess();
    }
    logger.warn("Resource {} does not exist or not in CREATING state", resource);
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
