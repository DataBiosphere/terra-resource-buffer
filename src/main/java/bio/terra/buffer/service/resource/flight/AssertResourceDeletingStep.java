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
public class AssertResourceDeletingStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(AssertResourceDeletingStep.class);

  private final BufferDao bufferDao;

  public AssertResourceDeletingStep(BufferDao bufferDao) {
    this.bufferDao = bufferDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    Optional<Resource> resource =
        bufferDao.retrieveResource(ResourceId.retrieve(flightContext.getInputParameters()));
    if (resource.isPresent() && resource.get().state().equals(ResourceState.DELETING)) {
      return StepResult.getStepResultSuccess();
    }
    logger.warn("Resource {} does not exist or not in DELETING state", resource);
    // Retry this steps to avoid the potential race that "submitting Flight and update DB"
    // transaction does not complete but the flight begins and it reaches here.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
