package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;

import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deletes the GCP project. */
public class DeleteProjectStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteProjectStep.class);
  private final CloudResourceManagerCow rmCow;

  public DeleteProjectStep(CloudResourceManagerCow rmCow) {
    this.rmCow = rmCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId =
        flightContext
            .getInputParameters()
            .get(CLOUD_RESOURCE_UID, CloudResourceUid.class)
            .getGoogleProjectUid()
            .getProjectId();
    try {
      Optional<Project> project = retrieveProject(rmCow, projectId);
      if (!project.isPresent() || isProjectDeleting(project.get())) {
        // Skip if project does not exist, or is being deleted. We know that the project is
        // created by Resource Buffer Service hence Resource Buffer Service should have owner
        // permission. So we assume 403 in this case
        // means that the project does not exist.
        logger.info("Project id: {} is deleted or being deleted", projectId);
        return StepResult.getStepResultSuccess();
      }
      OperationCow<?> operation =
          rmCow.operations().operationCow(rmCow.projects().delete(projectId).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
    } catch (IOException e) {
      logger.info("Error when deleting GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We can't undo deleting a project
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
