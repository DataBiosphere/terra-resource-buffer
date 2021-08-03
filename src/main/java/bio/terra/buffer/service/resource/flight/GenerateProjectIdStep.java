package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.retrieveProject;

import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.*;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates Project Id and put it in working map. */
public class GenerateProjectIdStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(GenerateProjectIdStep.class);

  private final GcpProjectConfig gcpProjectConfig;
  private final GcpProjectIdGenerator projectIdGenerator;
  private final CloudResourceManagerCow rmCow;

  public GenerateProjectIdStep(
      GcpProjectConfig gcpProjectConfig,
      GcpProjectIdGenerator projectIdGenerator,
      CloudResourceManagerCow rmCow) {
    this.gcpProjectConfig = gcpProjectConfig;
    this.projectIdGenerator = projectIdGenerator;
    this.rmCow = rmCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    try {
      String projectId =
          projectIdGenerator.generateIdWithRetries(gcpProjectConfig.getProjectIdSchema());

      // check that a project with this id does not already exist. to do this check, we try
      // to retrieve the project. this will only succeed if RBS has permission to get that
      // project. we expect this to be the case most of the time because of the Terra-specific
      // prefix, but it's possible this step will generate a project that is in use outside of
      // Terra. in that case, the project creation step will fail.
      if (retrieveProject(rmCow, projectId).isPresent()) {
        logger.info("Generated GCP project is already in use: {}", projectId);

        // here we use Stairway retry, instead of retrying immediately in order to rate limit
        // the number of project get calls made per step execution. i.e. we could retry the
        // project id generation immediately since we haven't hit a quota issue yet, but that
        // would mean each execution of this step may make a variable number of project get
        // calls. that would make it hard to decide how many flights could run in parallel
        // without hitting project get quota issues.
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }

      flightContext.getWorkingMap().put(GOOGLE_PROJECT_ID, projectId);
      workingMap.put(
          CLOUD_RESOURCE_UID,
          new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId)));
      return StepResult.getStepResultSuccess();
    } catch (IOException | InterruptedException ex) {
      logger.info("Error when generating GCP project id.", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
