package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.common.ResourceState;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.*;
import java.util.Optional;

/** .Step to set resource state to DELETING. */
public class InitialResourceDeletionStep implements Step {
  private final RbsDao rbsDao;

  public InitialResourceDeletionStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    ResourceId resourceId = ResourceId.retrieve(flightContext.getInputParameters());
    Optional<Resource> resource = rbsDao.retrieveResource(resourceId);
    if (resource.isPresent() && resource.get().state().equals(ResourceState.READY)) {
      // Only update READY state resource.
      rbsDao.updateResourceAsDeleting(resourceId);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    // Revert resource state back to READY to let RBS scheduling service pick it up again.
    // Currently we don't have FATAL state to prevent a bad state resource get into here again and
    // again. We rely on alerting + scripts to prevent this. At this moment, the deletion logic is
    // simple and it should
    // work for most cases unless GCP outrage happens in which case there is nothing we can do.
    if (rbsDao.updateResourceAsReady(
        ResourceId.retrieve(inputMap),
        inputMap.get(FlightMapKeys.CLOUD_RESOURCE_UID, CloudResourceUid.class))) {
      return StepResult.getStepResultSuccess();
    } else {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
    }
  }
}
