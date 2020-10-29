package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.io.IOException;
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
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId =
        flightContext
            .getInputParameters()
            .get(CLOUD_RESOURCE_UID, CloudResourceUid.class)
            .getGoogleProjectUid()
            .getProjectId();
    try {
      Optional<Project> project = retrieveProject(rmCow, projectId);
      if (!project.isPresent()
          || project.get().getLifecycleState().equals("DELETE_REQUESTED")
          || project.get().getLifecycleState().equals("DELETE_IN_PROGRESS")) {
        // Skip is project does not exist, is deleted or being deleted. We know that the project is
        // created by RBS hence RBS should have owner permission. So we assume 403 in this case means that the project
        // does not exist.
        logger.info("Project id: {} is deleted or being deleted", projectId);
        return StepResult.getStepResultSuccess();
      }
      rmCow.projects().delete(projectId).execute();
    } catch (IOException e) {
      logger.info("Error when deleting GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Noting to revert.
    return StepResult.getStepResultSuccess();
  }
}
