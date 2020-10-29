package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;
import static bio.terra.rbs.service.resource.flight.StepUtils.isResourceReady;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the basic GCP project. */
public class CreateProjectStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateProjectStep.class);
  private final CloudResourceManagerCow rmCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateProjectStep(CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
    this.rmCow = rmCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      // If the project id us used. Fail the flight and let Stairway rollback the flight.
      if (retrieveProject(rmCow, projectId).isPresent()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
      }
      Project project =
          new Project()
              .setProjectId(projectId)
              .setParent(
                  new ResourceId().setType("folder").setId(gcpProjectConfig.getParentFolderId()));
      OperationCow<?> operation =
          rmCow.operations().operationCow(rmCow.projects().create(project).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(10), Duration.ofMinutes(5));
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    if (isResourceReady(flightContext)) {
      return StepResult.getStepResultSuccess();
    }
    try {
      String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
      // Google returns 403 for projects we don't have access to and projects that don't exist.
      // We assume in this case that the project does not exist, not that somebody else has
      // created a project with the same random id.
      Optional<Project> project = getResource(() -> rmCow.projects().get(projectId).execute(), 403);
      if (!project.isPresent()) {
        // The project does not exist.
        return StepResult.getStepResultSuccess();
      }
      if (project.get().getLifecycleState().equals("DELETE_REQUESTED")
          || project.get().getLifecycleState().equals("DELETE_IN_PROGRESS")) {
        // The project is already being deleted.
        return StepResult.getStepResultSuccess();
      }
      rmCow.projects().delete(projectId).execute();
    } catch (IOException e) {
      logger.info("Error when deleting GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
