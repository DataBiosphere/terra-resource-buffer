package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Network;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates firewall rules for the GCP project. */
public class CreateFirewallRuleStep implements Step {
  private static final String ALLOW_INTERNAL_RULE_NAME = "allow-internal";
  private static final String LEONARDO_SSL_RULE_NAME = "leonardo-ssl";

  /**
   * See <a
   * href="https://cloud.google.com/vpc/docs/firewalls#more_rules_default_vpc">default-allow-internal</a>.
   */
  @VisibleForTesting
  public static final Firewall ALLOW_INTERNAL =
      new Firewall()
          .setName(ALLOW_INTERNAL_RULE_NAME)
          .setDescription("Allow internal traffic on the network.")
          .setDirection("INGRESS")
          .setSourceRanges(ImmutableList.of("10.128.0.0/9"))
          .setPriority(65534)
          .setAllowed(
              ImmutableList.of(
                  new Firewall.Allowed().setIPProtocol("icmp"),
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(ImmutableList.of("0-65535")),
                  new Firewall.Allowed()
                      .setIPProtocol("udp")
                      .setPorts(ImmutableList.of("0-65535"))));

  /** Allow SSL traffic from Leonardo-managed VMs. */
  @VisibleForTesting
  public static final Firewall LEONARDO_SSL =
      new Firewall()
          .setName(LEONARDO_SSL_RULE_NAME)
          .setDescription("Allow SSL traffic from Leonardo-managed VMs.")
          .setDirection("INGRESS")
          .setSourceRanges(ImmutableList.of("0.0.0.0/0"))
          .setTargetTags(ImmutableList.of("leonardo"))
          .setPriority(65534)
          .setAllowed(
              ImmutableList.of(
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(ImmutableList.of("443"))));

  private final Logger logger = LoggerFactory.getLogger(CreateFirewallRuleStep.class);
  private final CloudComputeCow computeCow;

  public CreateFirewallRuleStep(CloudComputeCow computeCow) {
    this.computeCow = computeCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      // Network is already created and checked in previous step so here won't be empty.
      // If we got NPE, that means something went wrong with GCP, fine to just throw NPE here.
      Network network =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();

      List<OperationCow<?>> operationsToPoll = new ArrayList<>();
      createResourceAndIgnoreConflict(
              () ->
                  computeCow
                      .firewalls()
                      .insert(projectId, ALLOW_INTERNAL.setNetwork(network.getSelfLink()))
                      .execute())
          .ifPresent(
              insertOperation ->
                  operationsToPoll.add(
                      computeCow.globalOperations().operationCow(projectId, insertOperation)));
      createResourceAndIgnoreConflict(
              () ->
                  computeCow
                      .firewalls()
                      .insert(projectId, LEONARDO_SSL.setNetwork(network.getSelfLink()))
                      .execute())
          .ifPresent(
              insertOperation ->
                  operationsToPoll.add(
                      computeCow.globalOperations().operationCow(projectId, insertOperation)));

      for (OperationCow<?> operation : operationsToPoll) {
        pollUntilSuccess(operation, Duration.ofSeconds(3), Duration.ofMinutes(5));
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating firewall rule", e);
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
