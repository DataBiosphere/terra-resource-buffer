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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deletes GCP statically-named firewall rules because we will manually created them later. */
public class DeleteDefaultFirewallRulesStep implements Step {
  /**
   * The default firewalls to delete. See <a
   * href="https://cloud.google.com/vpc/docs/firewalls#more_rules_default_vpc">Pre-populated rules
   * in the default network</a>.
   */
  public static final List<String> DEFAULT_FIREWALL_NAMES =
      ImmutableList.of(
          "default-allow-icmp", "default-allow-internal", "default-allow-rdp", "default-allow-ssh");

  private final Logger logger = LoggerFactory.getLogger(DeleteDefaultFirewallRulesStep.class);
  private final CloudComputeCow computeCow;

  public DeleteDefaultFirewallRulesStep(CloudComputeCow computeCow) {
    this.computeCow = computeCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      List<OperationCow<?>> operationsToPoll = new ArrayList<>();
      for (String firewallName : DEFAULT_FIREWALL_NAMES) {
        if (!resourceExists(
            () -> computeCow.firewalls().get(projectId, firewallName).execute(), 404)) {
          logger.info(
              "firewall rules {} is already deleted for project: {}.", firewallName, projectId);
        } else {
          operationsToPoll.add(
              computeCow
                  .globalOperations()
                  .operationCow(
                      projectId, computeCow.firewalls().delete(projectId, firewallName).execute()));
        }
      }

      for (OperationCow<?> operation : operationsToPoll) {
        pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error when deleting firewall rule", e);
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
