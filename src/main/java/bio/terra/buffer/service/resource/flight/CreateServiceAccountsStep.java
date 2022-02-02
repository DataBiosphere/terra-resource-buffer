package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.SubnetworkLogConfig;
import com.google.api.services.iam.v1.model.CreateServiceAccountRequest;
import com.google.api.services.iam.v1.model.ServiceAccount;
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
    // Skip if IAM binding is not set.
    if (gcpProjectConfig.getServiceAccounts() == null || gcpProjectConfig.getServiceAccounts().isEmpty()) {
      return StepResult.getStepResultSuccess();
    }
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    for(bio.terra.buffer.generated.model.ServiceAccount serviceAccount: gcpProjectConfig.getServiceAccounts()) {
      CreateServiceAccountRequest createRequest =
              new CreateServiceAccountRequest()
                      .setAccountId(serviceAccount.getName())
                      .setServiceAccount(
                              new ServiceAccount()
                                      // Set a description to help with debugging.
                                      .setDescription(serviceAccount.getDescription()));
      iamCow.projects().serviceAccounts().create("projects/" + projectId, createRequest).execute();
    }


    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks subnets exists or not. So no need to delete subnet.
    return StepResult.getStepResultSuccess();
  }

}
