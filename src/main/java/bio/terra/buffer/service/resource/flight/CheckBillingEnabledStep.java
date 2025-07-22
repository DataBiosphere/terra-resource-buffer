package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.cloud.billing.v1.ProjectBillingInfo;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.projectIdToName;

/** Sets up billing for project. */
public class CheckBillingEnabledStep implements Step {
  private final CloudBillingClientCow billingCow;

  public CheckBillingEnabledStep(CloudBillingClientCow billingCow) {
    this.billingCow = billingCow;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    ProjectBillingInfo billingInfo = billingCow.getProjectBillingInfo(projectIdToName(projectId));
    if (!billingInfo.getBillingEnabled()) {
      throw new RuntimeException(
          "Billing is not enabled for project: " + projectId);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
