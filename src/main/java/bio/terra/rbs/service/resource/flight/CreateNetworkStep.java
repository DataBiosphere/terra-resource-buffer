package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.pollUntilSuccess;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a VPC network within a GCP project created in a prior step. */
public class CreateNetworkStep implements Step {
  /** All project will use the same network name. */
  @VisibleForTesting public static final String NETWORK_NAME = "network";

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
    Network network = new Network().setName(NETWORK_NAME).setAutoCreateSubnetworks(false);
    try {
      OperationCow<?> operation =
          computeCow
              .globalOperations()
              .operationCow(projectId, computeCow.networks().insert(projectId, network).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
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
