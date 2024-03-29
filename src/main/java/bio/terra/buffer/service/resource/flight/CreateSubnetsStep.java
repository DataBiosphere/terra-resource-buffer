package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.getRegionToIpRange;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.isNetworkMonitoringEnabled;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.usePrivateGoogleAccess;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.SubnetworkLogConfig;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
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
    boolean networkMonitoringEnabled = isNetworkMonitoringEnabled(gcpProjectConfig);
    List<OperationCow<?>> operationsToPoll = new ArrayList<>();
    try {
      Network network =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();
      for (Map.Entry<String, String> entry : getRegionToIpRange(gcpProjectConfig).entrySet()) {
        String region = entry.getKey();
        Subnetwork subnetwork =
            new Subnetwork()
                .setName(SUBNETWORK_NAME)
                .setRegion(region)
                .setNetwork(network.getSelfLink())
                .setIpCidrRange(entry.getValue())
                .setEnableFlowLogs(networkMonitoringEnabled)
                .setPrivateIpGoogleAccess(usePrivateGoogleAccess(gcpProjectConfig));
        if (networkMonitoringEnabled) {
          subnetwork.setLogConfig(getSubnetLogConfig(subnetwork.getIpCidrRange()));
        }

        createResourceAndIgnoreConflict(
                () -> computeCow.subnetworks().insert(projectId, region, subnetwork).execute())
            .ifPresent(
                insertOperation ->
                    operationsToPoll.add(
                        computeCow
                            .regionalOperations()
                            .operationCow(projectId, region, insertOperation)));
      }

      // Kick off all the operations first then poll all operations
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
    // doStep methods already checks subnets exists or not. So no need to delete subnet.
    return StepResult.getStepResultSuccess();
  }

  /**
   * If flow logs are enabled, we want to adjust the default config in two ways:
   *
   * <ul>
   *   <li>Increase the sampling ratio (defaults to 0.5) so we sample all traffic.
   *   <li>Reduce the aggregation interval to 30 seconds (default is 5secs) to save on storage
   * </ul>
   *
   * <p>For log filter, when network monitoring is enabled. We use flow logs to monitor egress
   * network traffic. The filters are:
   *
   * <ul>
   *   <li>Reporter is SRC (egress)
   *   <li>Destination is not restricted.googleapi.com
   *   <li>Destination is not within the same network.
   * </ul>
   *
   * <p>Flow log filter does not support {@code has()} yet, the workaround is to filter out traffoc
   * between different networks is by using dest_ip. Ideally, it should be {@code
   * dest_instance.project_id != src_instance.project_id} See
   * https://b.corp.google.com/issues/171517286 (GCP internal support ticket) for discussion.
   */
  @VisibleForTesting
  public static SubnetworkLogConfig getSubnetLogConfig(String subnetIpRange) {
    String logFilter =
        "reporter=='SRC' && "
            + "!inIpRange(connection.dest_ip, '"
            + subnetIpRange
            + "') && "
            + "!inIpRange(connection.dest_ip, '"
            + RESTRICTED_GOOGLE_IP_ADDRESS
            + "')";
    return new SubnetworkLogConfig()
        .setAggregationInterval("INTERVAL_30_SEC")
        .setFilterExpr(logFilter)
        .setEnable(true)
        .setFlowSampling((float) 1.0)
        .setMetadata("INCLUDE_ALL_METADATA");
  }
}
