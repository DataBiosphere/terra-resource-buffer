package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_NUMBER;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes the default Compute Engine service account in the project. See <a
 * href="https://cloud.google.com/iam/docs/service-accounts#default">Default service accounts</a>
 */
public class DeleteDefaultServiceAccountStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteDefaultServiceAccountStep.class);
  private final IamCow iamCow;

  public DeleteDefaultServiceAccountStep(IamCow iamCow) {
    this.iamCow = iamCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    Long projectNumber = flightContext.getWorkingMap().get(GOOGLE_PROJECT_NUMBER, Long.class);
    try {
      iamCow
          .projects()
          .serviceAccounts()
          .delete(getServiceAccountName(projectId, projectNumber))
          .execute();
    } catch (IOException e) {
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getStatusCode() == 404) {
        logger.info("Service Account does not exist", e);
        // Mark step success if the SA account does not exist or already deleted.
        return StepResult.getStepResultSuccess();
      }
      logger.info("Error when deleting service account", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /** The default Compute Engine service account name. */
  public String getServiceAccountName(String projectId, Long projectNumber) {
    return "projects/"
        + projectId
        + "/serviceAccounts/"
        + projectNumber
        + "-compute@developer.gserviceaccount.com";
  }
}
