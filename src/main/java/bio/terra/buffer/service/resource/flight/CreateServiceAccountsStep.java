package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.SubnetworkLogConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.isNetworkMonitoringEnabled;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.usePrivateGoogleAccess;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;

/** Creates Service accounts and garnt permission for projects */
public class CreateServiceAccountsStep implements Step {

  private final Logger logger = LoggerFactory.getLogger(CreateServiceAccountsStep.class);
  private final IamCow iamCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateServiceAccountsStep(IamCow iamCow, GcpProjectConfig gcpProjectConfig) {
    this.iamCow = iamCow;
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
      for (Map.Entry<String, String> entry : getRegionToIpRange().entrySet()) {
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

  /** Gets a map of region to IP range. */
  private Map<String, String> getRegionToIpRange() {
    List<String> blockedRegions = GoogleProjectConfigUtils.blockedRegions(gcpProjectConfig);
    return REGION_TO_IP_RANGE.entrySet().stream()
        .filter(e -> !blockedRegions.contains(e.getKey()))
        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
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
