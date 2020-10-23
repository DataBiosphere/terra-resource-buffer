package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.CreateNetworkStep.NETWORK_NAME;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.enableNetworkMonitoring;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.pollUntilSuccess;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Craetes Subnetworks for project */
public class CreateSubnetsStep implements Step {
  @VisibleForTesting public static final String SUBNETWORK_NAME = "subnetwork";
  /** All current Google Compute Engine regions, value from: {@code gcloud compute regions list}. */
  @VisibleForTesting
  public static final List<String> SUBNETS_REGIONS =
      ImmutableList.of(
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

  private final Logger logger = LoggerFactory.getLogger(CreateSubnetsStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateSubnetsStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    boolean networkMonitoringEnabled = enableNetworkMonitoring(gcpProjectConfig);
    List<OperationCow<?>> operationsToPoll = new ArrayList<>();
    try {
      Network network = computeCow.networks().get(projectId, NETWORK_NAME).execute();
      for (int i = 0; i < SUBNETS_REGIONS.size(); i++) {
        String region = SUBNETS_REGIONS.get(i);
        try {
          // Skip this steps if subnet already exists.
          computeCow.subnetworks().get(projectId, region, SUBNETWORK_NAME).execute();
          logger.info("Subnets already exists for project {}, region: {}.", projectId, region);
          continue;
        } catch (IOException e) {
          if (e instanceof GoogleJsonResponseException
              && ((GoogleJsonResponseException) e).getStatusCode() == 404) {
            // do nothing
          } else {
            return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
          }
        }
        Subnetwork subnetwork =
            new Subnetwork()
                .setName(SUBNETWORK_NAME)
                .setRegion(region)
                .setNetwork(network.getSelfLink())
                .setIpCidrRange(generateIpRange(i))
                .setEnableFlowLogs(networkMonitoringEnabled)
                .setPrivateIpGoogleAccess(networkMonitoringEnabled);
        operationsToPoll.add(
            computeCow
                .regionalOperations()
                .operationCow(
                    projectId,
                    region,
                    computeCow.subnetworks().insert(projectId, region, subnetwork).execute()));
      }

      // Batch poll to make it faster
      for (OperationCow<?> operation : operationsToPoll) {
        pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating subnets", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /**
   * Generates Ip ranges programmatically.
   *
   * @see <a
   *     href="https://github.com/broadinstitute/gcp-dm-templates/blob/eeee90fa4619b273d07206a867b01914cdeb0a30/firecloud_project.py#L49">gcp-dm-templates</a>
   */
  @VisibleForTesting
  public static String generateIpRange(int i) {
    return "10." + (128 + 2 * i) + ".0.0/20";
  }
}
