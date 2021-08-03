package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.*;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;
import static bio.terra.buffer.service.resource.flight.StepUtils.isResourceReady;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the basic GCP project. */
public class CreateProjectStep implements Step {
  @VisibleForTesting public static final String NETWORK_LABEL_KEY = "vpc-network-name";
  @VisibleForTesting public static final String SUB_NETWORK_LABEL_KEY = "vpc-subnetwork-name";
  @VisibleForTesting public static final String CONFIG_NAME_LABEL_LEY = "buffer-config-name";

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
              .setLabels(createLabelMap(flightContext))
              .setParent("folders/" + gcpProjectConfig.getParentFolderId());
      OperationCow<?> operation =
          rmCow.operations().operationCow(rmCow.projects().create(project).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
      Project createdProject = rmCow.projects().get(projectId).execute();
      flightContext.getWorkingMap().put(GOOGLE_PROJECT_NUMBER, getNumber(createdProject));
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
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
      if (isProjectDeleting(project.get())) {
        // The project is already being deleted.
        return StepResult.getStepResultSuccess();
      }
      OperationCow<?> operation =
          rmCow.operations().operationCow(rmCow.projects().delete(projectId).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
    } catch (IOException | RetryException e) {
      logger.info("Error when deleting GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Creates labels for the a GCP projects including network name, sub network name, and the
   * Resource Buffer Service resource config name it uses.
   */
  private static Map<String, String> createLabelMap(FlightContext flightContext) {
    return new ImmutableMap.Builder<String, String>()
        .put(NETWORK_LABEL_KEY, createValidLabelValue(NETWORK_NAME))
        .put(SUB_NETWORK_LABEL_KEY, createValidLabelValue(SUBNETWORK_NAME))
        .put(
            CONFIG_NAME_LABEL_LEY,
            createValidLabelValue(
                flightContext
                    .getInputParameters()
                    .get(RESOURCE_CONFIG, ResourceConfig.class)
                    .getConfigName()))
        .build();
  }

  /**
   * Creates a valid GCP label value to meet the requirement. See <a
   * href='https://cloud.google.com/deployment-manager/docs/creating-managing-labels#requirements'>Requirements
   * for labels</a>
   */
  @VisibleForTesting
  public static String createValidLabelValue(String originalName) {
    String regex = "[^a-z0-9-_]+";
    String value = originalName.toLowerCase().replaceAll(regex, "--");
    return value.length() > 64 ? value.substring(0, 63) : value;
  }

  /** Returns the uniquely identifying number of the project. */
  private static Long getNumber(Project project) {
    // The projects name has the form "projects/[project number]".
    return Long.parseLong(project.getName().substring("projects/".length()));
  }
}
