package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;

/** Dummy {@link CreateGoogleProjectStep} which fails in doStep. */
public class ErrorCreateGoogleProjectStep extends CreateGoogleProjectStep {
  public ErrorCreateGoogleProjectStep(
      CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
    super(rmCow, gcpProjectConfig);
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
