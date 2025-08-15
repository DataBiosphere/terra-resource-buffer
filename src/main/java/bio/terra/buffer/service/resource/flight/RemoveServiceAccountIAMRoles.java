package bio.terra.buffer.service.resource.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static bio.terra.buffer.service.resource.FlightMapKeys.*;

/** Enable services for project. */
public class RemoveServiceAccountIAMRoles implements Step {
  private final Logger logger = LoggerFactory.getLogger(RemoveServiceAccountIAMRoles.class);
  private final CloudResourceManagerCow rmCow;

  public RemoveServiceAccountIAMRoles(CloudResourceManagerCow rmCow) {
    this.rmCow = rmCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getInputParameters().get(GOOGLE_PROJECT_ID, String.class);

    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      String clientEmail = ((com.google.auth.oauth2.ServiceAccountCredentials) credentials).getClientEmail();
      String memberToRemove = "serviceAccount:" + clientEmail;
      List<String> rolesToRemove = List.of("roles/serviceusage.serviceUsageAdmin", "roles/resourcemanager.projectIamAdmin");

      Policy policy = rmCow.projects().getIamPolicy(projectId, new GetIamPolicyRequest()).execute();
      Policy updatedPolicy = GoogleUtils.removeUserRolesFromPolicy(policy, memberToRemove, rolesToRemove);
      rmCow.projects()
            .setIamPolicy(projectId, new SetIamPolicyRequest().setPolicy(updatedPolicy))
            .execute();
      logger.info("Removed IAM roles {} for member {}", rolesToRemove, memberToRemove);
    } catch (IOException e) {
      logger.info("Error when removing IAM policy", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
