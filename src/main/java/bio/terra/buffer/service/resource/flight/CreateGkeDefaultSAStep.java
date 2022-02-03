package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.createGkeDefaultSa;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import com.google.api.services.iam.v1.model.CreateServiceAccountRequest;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Creates Service accounts for running Google Compute Engine
 *
 * <p>This replaces the default Compute Engine Service Account. See
 * https://cloud.google.com/kubernetes-engine/docs/how-to/hardening-your-cluster#use_least_privilege_sa
 */
public class CreateGkeDefaultSAStep implements Step {
  public static final String GKE_SA_NAME = "gke_node_default_sa";
  public static final Set<String> GKE_SA_ROLES =
      ImmutableSet.of(
          "roles/logging.logWriter",
          "roles/monitoring.metricWriter",
          "roles/monitoring.viewer",
          "roles/stackdriver.resourceMetadata.writer");

  private final Logger logger = LoggerFactory.getLogger(CreateGkeDefaultSAStep.class);
  private final IamCow iamCow;
  private final CloudResourceManagerCow rmCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateGkeDefaultSAStep(
      IamCow iamCow, CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
    this.iamCow = iamCow;
    this.rmCow = rmCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    if (!createGkeDefaultSa(gcpProjectConfig)) {
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
      logger.warn("Service account {} already created for notebook instance.", GKE_SA_NAME);
    } catch (IOException e) {
      throw new RetryException(e);
    }

    // Grants permission that a GKE node runner needs
    String serviceAccountEmail = ServiceAccountName.emailFromAccountId(GKE_SA_NAME, projectId);

    try {
      Policy policy = rmCow.projects().getIamPolicy(projectId, new GetIamPolicyRequest()).execute();
      GKE_SA_ROLES.forEach(
          r ->
              policy
                  .getBindings()
                  .add(
                      new Binding()
                          .setRole(r)
                          .setMembers(Collections.singletonList(serviceAccountEmail))));
      // Duplicating bindings is harmless (e.g. on retry). GCP de-duplicates.
      rmCow
          .projects()
          .setIamPolicy(projectId, new SetIamPolicyRequest().setPolicy(policy))
          .execute();

    } catch (IOException e) {
      logger.info("Error when setting IAM policy for GKE default node SA", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
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
