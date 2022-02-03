package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.createGkeDefaultSa;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.CreateServiceAccountRequest;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Optional;

/**
 * Creates Service accounts for running Google Compute Engine
 *
 * <p> This replaces the default Compute Engine Service Account. See
 * https://cloud.google.com/kubernetes-engine/docs/how-to/hardening-your-cluster#use_least_privilege_sa
 */
public class CreateGkeDefaultSAStep implements Step {
  public static final String GKE_SA_NAME = "gke_node_default_sa";

  private final Logger logger = LoggerFactory.getLogger(CreateGkeDefaultSAStep.class);
  private final IamCow iamCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateGkeDefaultSAStep(IamCow iamCow, GcpProjectConfig gcpProjectConfig) {
    this.iamCow = iamCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    if(!createGkeDefaultSa(gcpProjectConfig)) {
      return StepResult.getStepResultSuccess();
    }
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    CreateServiceAccountRequest createRequest =
            new CreateServiceAccountRequest()
                    .setAccountId(GKE_SA_NAME)
                    .setServiceAccount(
                            new ServiceAccount()
                                    // Set a description to help with debugging.
                                    .setDescription("Default service account can be used on GKE node. "));
    try {
      iamCow.projects().serviceAccounts().create("projects/" + projectId, createRequest).execute();
    } catch (GoogleJsonResponseException e) {
      // If the service account already exists, this step must have run already.
      // Otherwise throw a retry exception.
      if (e.getStatusCode() != HttpStatus.CONFLICT.value()) {
        throw new RetryException(e);
      }
      logger.warn(
              "Service account {} already created for notebook instance.", GKE_SA_NAME);
    } catch (IOException e) {
      throw new RetryException(e);
    }

    // Grants permission that a GKE node runner needs
    String serviceAccountEmail =
            ServiceAccountName.emailFromAccountId(
                    flightContext.getWorkingMap().get(GKE_SA_NAME, String.class),
                    projectId);
    ServiceAccountName serviceAccountName =
            ServiceAccountName.builder().projectId(projectId).email(serviceAccountEmail).build();
    try {
      Policy policy = iamCow.projects().serviceAccounts().getIamPolicy(GKE_SA_NAME).execute();
      // Duplicating bindings is harmless (e.g. on retry). GCP de-duplicates.
      Optional.ofNullable(policy.getBindings()).ifPresent(newBindings::addAll);
      policy.setBindings(newBindings);
      iamCow.projects()
              .serviceAccounts()
              .setIamPolicy(serviceAccountName, new SetIamPolicyRequest().setPolicy(policy))
              .execute();
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks subnets exists or not. So no need to delete subnet.
    return StepResult.getStepResultSuccess();
  }
}
