package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.CreateNetworkStep.NETWORK_NAME;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.enableNetworkMonitoring;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.pollUntilSuccess;

/** Craetes Subnetworks for project */
public class CreateSubnetsStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateSubnetsStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  private static final String SUBNET_NAME_PREFIX = "subnetwork_";
  /** All current Google Compute Engine regions, value from: {@code gcloud compute regions list}. */
  private static final List<String> SUBNETS_REGIONS = ImmutableList.of(
  "asia-east1",
  "asia-east2",
  "asia-northeast1",
  "asia-northeast2",
  "asia-northeast3",
  "asia-south1",
  "asia-southeast1",
  "asia-southeast2",
  "australia-southeast1",
  "europe-north1",
  "europe-west1",
  "europe-west2",
  "europe-west3",
  "europe-west4",
  "europe-west6",
  "northamerica-northeast1",
  "southamerica-east1",
  "us-central1",
  "us-east1",
  "us-east4",
  "us-west1",
  "us-west2",
  "us-west3",
  "us-west4");

  public CreateSubnetsStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    Network network = new Network().setName(NETWORK_NAME).setAutoCreateSubnetworks(false);
    boolean networkMonitoringEnabled = enableNetworkMonitoring(gcpProjectConfig);
    try {
      for(String region : SUBNETS_REGIONS) {
        String subnetName = SUBNET_NAME_PREFIX + region;
        Subnetwork subnetwork =
                new Subnetwork()
                        .setName(subnetName)
                        .setNetwork(network.getSelfLink())
                        .setIpCidrRange(ipCidrRange);
      }
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
    return StepResult.getStepResultSuccess();
  }
}
