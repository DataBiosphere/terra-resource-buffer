package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Route;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates static route to route allows APIs through a single narrow IP
 * range(restricted.googleapis.com).
 *
 * <p>See <a
 * href="https://cloud.google.com/vpc-service-controls/docs/set-up-private-connectivity#configuring-routes">Configuring
 * a custom static route in a VPC network</a>
 */
public class CreateRouteStep implements Step {
  @VisibleForTesting public static final String ROUTE_NAME = "private-google-access-route";
  /** restricted.googleapis.com */
  @VisibleForTesting public static final String DESTINATION_RANGE = "199.36.153.4/30";

  @VisibleForTesting
  public static final String DEFAULT_GATEWAY = "/global/gateways/default-internet-gateway";

  private final Logger logger = LoggerFactory.getLogger(CreateRouteStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateRouteStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    if (!checkEnableNetworkMonitoring(gcpProjectConfig)) {
      return StepResult.getStepResultSuccess();
    }
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      Network network =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();

      if (resourceExists(() -> computeCow.routes().get(projectId, ROUTE_NAME).execute(), 404)) {
        return StepResult.getStepResultSuccess();
      }
      Route route =
          new Route()
              .setName(ROUTE_NAME)
              .setDestRange(DESTINATION_RANGE)
              .setNetwork(network.getSelfLink())
              .setNextHopGateway("projects/" + projectId + DEFAULT_GATEWAY);

      OperationCow<?> operation =
          computeCow
              .globalOperations()
              .operationCow(projectId, computeCow.routes().insert(projectId, route).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating route", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks routes exists or not. So no need to delete route.
    return StepResult.getStepResultSuccess();
  }
}