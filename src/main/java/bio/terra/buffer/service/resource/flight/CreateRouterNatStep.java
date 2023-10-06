package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.FlightMapKeys.NAT_CREATED_REGIONS;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.enableNatGateway;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.getRegionToIpRange;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NAT_NAME_PREFIX;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NAT_ROUTER_NAME_PREFIX;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.createResourceAndIgnoreConflict;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.networkUri;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.pollUntilSuccess;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Router;
import com.google.api.services.compute.model.RouterNat;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Step to create NAT gateway per regions. */
public class CreateRouterNatStep implements Step {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateRouterNatStep.class);
  private final GcpProjectConfig gcpProjectConfig;
  private final CloudComputeCow computeCow;
  /** All of the IP ranges in every Subnetwork are allowed to Nat. */
  @VisibleForTesting
  public static final String SUBNETWORK_IP_RANGES_TO_NAT = "ALL_SUBNETWORKS_ALL_IP_RANGES";
  /** Nat IPs are allocated by Google Cloud Platform; customers can't specify any Nat IPs. */
  @VisibleForTesting public static final String NAT_IP_ALLOCATION = "AUTO_ONLY";

  public CreateRouterNatStep(GcpProjectConfig config, CloudComputeCow computeCow) {
    gcpProjectConfig = config;
    this.computeCow = computeCow;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (!enableNatGateway(gcpProjectConfig)) {
      return StepResult.getStepResultSuccess();
    }

    String projectId = context.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    Set<String> natCreatedRegions = new HashSet<>();
    if (context.getWorkingMap().containsKey(NAT_CREATED_REGIONS)) {
      natCreatedRegions =
          context.getWorkingMap().get(NAT_CREATED_REGIONS, new TypeReference<>() {});
    }
    var regions = getRegionToIpRange(gcpProjectConfig).keySet();
    for (String region : regions) {
      if (natCreatedRegions.contains(region)) {
        continue;
      }
      RouterNat natGateway =
          new RouterNat()
              .setName(NAT_NAME_PREFIX + region)
              .setSourceSubnetworkIpRangesToNat(SUBNETWORK_IP_RANGES_TO_NAT)
              .setNatIpAllocateOption(NAT_IP_ALLOCATION);
      Router router =
          new Router()
              .setName(NAT_ROUTER_NAME_PREFIX + region)
              .setRegion(region)
              .setNetwork(networkUri(projectId, NETWORK_NAME))
              .setNats(List.of(natGateway));
      try {
        Optional<Operation> insertOperation =
            createResourceAndIgnoreConflict(
                () -> computeCow.routers().insert(projectId, region, router).execute());
        if (insertOperation.isPresent()) {
          LOGGER.info(
              "start pulling insert router operation in project {} region {}", projectId, region);
          OperationCow<?> operation =
              computeCow
                  .regionalOperations()
                  .operationCow(projectId, region, insertOperation.get());
          pollUntilSuccess(operation, Duration.ofSeconds(3), Duration.ofMinutes(5));
          natCreatedRegions.add(region);
          context.getWorkingMap().put(NAT_CREATED_REGIONS, natCreatedRegions);
        }
      } catch (IOException e) {
        LOGGER.info("Error when creating Nat router", e);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    return StepResult.getStepResultSuccess();
  }
}
