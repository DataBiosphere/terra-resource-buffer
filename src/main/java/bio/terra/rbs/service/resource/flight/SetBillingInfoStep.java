package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.projectIdToName;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.cloud.billing.v1.ProjectBillingInfo;

/** Sets up billing for project. */
public class SetBillingInfoStep implements Step {
  private final CloudBillingClientCow billingCow;
  private final GcpProjectConfig gcpProjectConfig;

  public SetBillingInfoStep(CloudBillingClientCow billingCow, GcpProjectConfig gcpProjectConfig) {
    this.billingCow = billingCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    // Skip if billing account is not set.
    if (gcpProjectConfig.getBillingAccount() == null
        || gcpProjectConfig.getBillingAccount().isEmpty()) {
      return StepResult.getStepResultSuccess();
    }
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    ProjectBillingInfo setBilling =
        ProjectBillingInfo.newBuilder()
            .setBillingAccountName("billingAccounts/" + gcpProjectConfig.getBillingAccount())
            .build();
    billingCow.updateProjectBillingInfo(projectIdToName(projectId), setBilling);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
