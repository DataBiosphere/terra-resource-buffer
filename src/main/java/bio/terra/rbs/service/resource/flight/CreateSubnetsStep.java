package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.NETWORK_NAME;

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
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates Subnetworks for project
 *
 * <p>This class implements the creation of a non-default VPC network within a GCP project, per the
 * CIS benchmark guidelines for Google Cloud Platform ("3.1 Ensure the default network does not
 * exist in a project"). The default network automatically maintains a subnetwork in each active GCP
 * region; the goal of this code is to recreate a setup that is very close to how the default VPC
 * network with auto-subnets operates, but with hard-coded regions and IP ranges.
 *
 * <p>As of Q4 2020, Terra doesn't have a strong driver for regional resources outside of the
 * Broad's default zone (us-central1), so this list of subnets is not carefully curated or
 * automatically extended as new GCP regions are added.
 *
 * <p>If the list of subnets managed here grows (to support new cloud regions) or shrinks (to reduce
 * the space of preallocated IPs), a manual backfill process would be required to update the set of
 * subnetworks in existing Terra workspaces.
 */
public class CreateSubnetsStep implements Step {
  @VisibleForTesting public static final String SUBNETWORK_NAME = "subnetwork";
  /** All current Google Compute Engine regions, value from: {@code gcloud compute regions list}. */
  @VisibleForTesting
  public static final List<String> SUBNET_REGIONS =
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

  public static final Map<String, String> REGION_TO_IP_RANGE = new HashMap<>();

  private final Logger logger = LoggerFactory.getLogger(CreateSubnetsStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateSubnetsStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  /**
   * Generates Ip ranges programmatically.
   *
   * @see <a
   *     href="https://github.com/broadinstitute/gcp-dm-templates/blob/eeee90fa4619b273d07206a867b01914cdeb0a30/firecloud_project.py#L49">gcp-dm-templates</a>
   */
  static {
    for (int i = 0; i < SUBNET_REGIONS.size(); i++) {
      REGION_TO_IP_RANGE.put(SUBNET_REGIONS.get(i), "10." + (128 + 2 * i) + ".0.0/20");
    }
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    boolean networkMonitoringEnabled = checkEnableNetworkMonitoring(gcpProjectConfig);
    List<OperationCow<?>> operationsToPoll = new ArrayList<>();
    try {
      Network network = computeCow.networks().get(projectId, NETWORK_NAME).execute();
      for (String region : SUBNET_REGIONS) {
        if (cloudObjectExists(
            () -> computeCow.subnetworks().get(projectId, region, SUBNETWORK_NAME).execute())) {
          continue;
        }
        Subnetwork subnetwork =
            new Subnetwork()
                .setName(SUBNETWORK_NAME)
                .setRegion(region)
                .setNetwork(network.getSelfLink())
                .setIpCidrRange(REGION_TO_IP_RANGE.get(region))
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

      // Kicking off all the operations first then polling all operations.
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
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks Sunets exists or not. So no need to delete subnet.
    return StepResult.getStepResultSuccess();
  }
}
