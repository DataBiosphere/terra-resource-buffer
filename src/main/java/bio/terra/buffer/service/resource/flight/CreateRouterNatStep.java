package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.blockBatchInternetAccess;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.enableNatGateway;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.getRegionToIpRange;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NAT_ROUTER_NAME_PREFIX;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Router;
import com.google.api.services.compute.model.RouterNat;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step to create NAT gateway per regions.
 */
public class CreateRouterNatStep implements Step {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateRouterNatStep.class);
  private final GcpProjectConfig gcpProjectConfig;
  private final CloudComputeCow computeCow;

  public CreateRouterNatStep(GcpProjectConfig config, CloudComputeCow computeCow) {
    gcpProjectConfig = config;
    this.computeCow = computeCow;
  }
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (!blockBatchInternetAccess(gcpProjectConfig) || !enableNatGateway(gcpProjectConfig)) {
      return StepResult.getStepResultSuccess();
    }

    String projectId = context.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    var regions = getRegionToIpRange(gcpProjectConfig).keySet();
    // Create a Compute Engine client
   for (String region : regions) {
     // Define the NAT gateway configuration
     RouterNat natGateway =
         new RouterNat()
             .setName("nat-gateway-" + region)
             .setSourceSubnetworkIpRangesToNat("ALL_SUBNETWORKS_ALL_IP_RANGES")
             .setNatIpAllocateOption("AUTO_ONLY");
     Router router = new Router().setName(NAT_ROUTER_NAME_PREFIX + region).setRegion(region)
         .setNetwork(NETWORK_NAME).setNats(List.of(natGateway));
     try {
       Operation insertRouterOperation = computeCow.routers().insert(projectId, region, router)
           .execute();
       computeCow.regionalOperations().operationCow(projectId, region, insertRouterOperation);
     } catch (IOException e) {
       LOGGER.info("Error when creating Nat router", e);
       return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
     }
   }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
