package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.pollUntilSuccess;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.resourceExists;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a VPC network within a GCP project created in a prior step. */
public class CreateNetworkStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(CreateNetworkStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateNetworkStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      // Skip this steps if network already exists. This may happen when previous step's polling
      // times out, while network is created before next retry.
      if (resourceExists(
          () -> computeCow.networks().get(projectId, GoogleUtils.NETWORK_NAME).execute(), 404)) {
        logger.info(
            "Network already exists for project %s: {}. Skipping CreateNetworkStep", projectId);
        return StepResult.getStepResultSuccess();
      }

      Network network =
          new Network().setName(GoogleUtils.NETWORK_NAME).setAutoCreateSubnetworks(false);
      OperationCow<?> operation =
          computeCow
              .globalOperations()
              .operationCow(projectId, computeCow.networks().insert(projectId, network).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(3), Duration.ofMinutes(5));
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating network", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP.
    return StepResult.getStepResultSuccess();
  }
}
