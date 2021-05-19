package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.*;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sets IAM Policy for project */
public class SetIamPolicyStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(SetIamPolicyStep.class);
  private final CloudResourceManagerCow rmCow;
  private final GcpProjectConfig gcpProjectConfig;

  public SetIamPolicyStep(CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
    this.rmCow = rmCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    // Skip if IAM binding is not set.
    if (gcpProjectConfig.getIamBindings() == null || gcpProjectConfig.getIamBindings().isEmpty()) {
      return StepResult.getStepResultSuccess();
    }

    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    try {
      Policy policy = rmCow.projects().getIamPolicy(projectId, new GetIamPolicyRequest()).execute();
      gcpProjectConfig.getIamBindings().stream()
          .map(
              iamBinding ->
                  new Binding().setRole(iamBinding.getRole()).setMembers(iamBinding.getMembers()))
          .forEach(policy.getBindings()::add);
      rmCow
          .projects()
          .setIamPolicy(projectId, new SetIamPolicyRequest().setPolicy(policy))
          .execute();

    } catch (IOException e) {
      logger.info("Error when setting IAM policy", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
