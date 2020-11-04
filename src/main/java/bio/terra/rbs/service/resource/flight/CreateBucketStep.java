package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;
import static bio.terra.rbs.service.resource.flight.StepUtils.isResourceReady;

/** Creates the basic GCP project. */
public class CreateBucketStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateBucketStep.class);
  private final ClientConfig clientConfig;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateBucketStep(ClientConfig clientConfig, GcpProjectConfig gcpProjectConfig) {
    this.clientConfig = clientConfig;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    StorageCow storageCow = new StorageCow(clientConfig, StorageOptions.newBuilder().setProjectId(projectId).build());
    String bucketName = "storage-logs-" + projectId;
    if(storageCow.get(bucketName) != null) {
      return StepResult.getStepResultSuccess();
    }
    BucketInfo.LifecycleRule rule = new BucketInfo.LifecycleRule(BucketInfo.LifecycleRule.LifecycleAction.newDeleteAction(),
            BucketInfo.LifecycleRule.LifecycleCondition.newBuilder().setAge(180).build());
    Acl acl = Acl.newBuilder().setEntity().build()
    storageCow.create(BucketInfo.newBuilder(bucketName).setLifecycleRules(ImmutableList.of(rule)).build());
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
      if (isProjectDeleting(project.get())) {
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
