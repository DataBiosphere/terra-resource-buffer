package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.keepDefaultNetwork;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.DEFAULT_NETWORK_NAME;
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
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Delete the default network because we will manually create it later. */
public class DeleteDefaultNetworkStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteDefaultNetworkStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public DeleteDefaultNetworkStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    // TODO: revisit whether we still need this flag after NF allows specifying a network
    // https://broadworkbench.atlassian.net/browse/PF-538
    if (keepDefaultNetwork(gcpProjectConfig)) {
      logger.info("Skipping deletion of default network");
      return StepResult.getStepResultSuccess();
    }

    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      // Skip this steps if network already exists. This may happen when previous step's polling
      // times out, while network is created before next retry.
      if (!resourceExists(
          () -> computeCow.networks().get(projectId, DEFAULT_NETWORK_NAME).execute(), 404)) {
        logger.info(
            "Default network: {} is already deleted for project: {}. Skipping DeleteDefaultNetworkStep",
            DEFAULT_NETWORK_NAME,
            projectId);
        return StepResult.getStepResultSuccess();
      }

      OperationCow<?> operation =
          computeCow
              .globalOperations()
              .operationCow(
                  projectId,
                  computeCow.networks().delete(projectId, DEFAULT_NETWORK_NAME).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(3), Duration.ofMinutes(5));
    } catch (IOException | InterruptedException e) {
      logger.info("Error when deleting default network", e);
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
