package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_NUMBER;
import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME;
import static bio.terra.buffer.service.resource.flight.CreateFirewallRuleStep.LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME;
import static bio.terra.buffer.service.resource.flight.CreateGkeDefaultSAStep.GKE_SA_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.createGkeDefaultSa;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.getSecurityGroup;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.SUBNETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.getResource;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.isProjectDeleting;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.pollUntilSuccess;
import static bio.terra.buffer.service.resource.flight.StepUtils.isResourceReady;

/** Creates the basic GCP project. */
public class CreateProjectStep implements Step {
  @VisibleForTesting public static final String NETWORK_LABEL_KEY = "vpc-network-name";
  @VisibleForTesting public static final String SUB_NETWORK_LABEL_KEY = "vpc-subnetwork-name";
  @VisibleForTesting public static final String CONFIG_NAME_LABEL_KEY = "buffer-config-name";
  // GKE default service account name label. Only sets when createGkeDefaultServiceAccount is true.
  @VisibleForTesting public static final String GKE_DEFAULT_SA_LABEL_KEY = "gke-default-sa";
  // Firewall rule name to allow https traffic for leonardo VMs. Empty if not having such firewall
  // rule.
  @VisibleForTesting
  public static final String LEONARDO_ALLOW_HTTPS_FIREWALL_RULE_NAME_LABEL_KEY =
      "leonardo-allow-https-firewall-name";
  // Firewall rule name to allow internal traffic within VPC for leonardo VMs. Empty if not having
  // such firewall rule.
  @VisibleForTesting
  public static final String LEONARDO_ALLOW_INTERNAL_RULE_NAME_LABEL_KEY =
      "leonardo-allow-internal-firewall-name";

  // Security Group used to determine allow-list to be used
  @VisibleForTesting public static final String SECURITY_GROUP_LABEL_KEY = "security-group";

  private final Logger logger = LoggerFactory.getLogger(CreateProjectStep.class);
  private final CloudResourceManagerCow rmCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateProjectStep(CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
    this.rmCow = rmCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      Project project =
          new Project()
              .setProjectId(projectId)
              .setLabels(createLabelMap(flightContext, gcpProjectConfig))
              .setParent("folders/" + gcpProjectConfig.getParentFolderId());
      OperationCow<?> operation =
          rmCow.operations().operationCow(rmCow.projects().create(project).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
      Project createdProject = rmCow.projects().get(projectId).execute();
      flightContext.getWorkingMap().put(GOOGLE_PROJECT_NUMBER, getNumber(createdProject));
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    if (isResourceReady(flightContext)) {
      return StepResult.getStepResultSuccess();
    }
    try {
      String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
      // Google returns 403 for projects we don't have access to and projects that don't exist.
      // We assume in this case that the project does not exist, not that somebody else has
      // created a project with the same random id.
      Optional<Project> project = getResource(() -> rmCow.projects().get(projectId).execute(), 403);
      if (project.isEmpty()) {
        // The project does not exist.
        return StepResult.getStepResultSuccess();
      }
      if (isProjectDeleting(project.get())) {
        // The project is already being deleted.
        return StepResult.getStepResultSuccess();
      }
      OperationCow<?> operation =
          rmCow.operations().operationCow(rmCow.projects().delete(projectId).execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
    } catch (IOException | RetryException e) {
      logger.info("Error when deleting GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Creates labels for the a GCP projects including network name, sub network name, and the
   * Resource Buffer Service resource config name it uses.
   */
  private static Map<String, String> createLabelMap(
      FlightContext flightContext, GcpProjectConfig gcpProjectConfig) {
    ImmutableMap.Builder<String, String> labelBuilder =
        new ImmutableMap.Builder<String, String>()
            .put(NETWORK_LABEL_KEY, createValidLabelValue(NETWORK_NAME))
            .put(SUB_NETWORK_LABEL_KEY, createValidLabelValue(SUBNETWORK_NAME))
            .put(
                LEONARDO_ALLOW_HTTPS_FIREWALL_RULE_NAME_LABEL_KEY,
                createValidLabelValue(LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME))
            .put(
                LEONARDO_ALLOW_INTERNAL_RULE_NAME_LABEL_KEY,
                createValidLabelValue(ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME))
            .put(
                CONFIG_NAME_LABEL_KEY,
                createValidLabelValue(
                    Objects.requireNonNull(
                            flightContext
                                .getInputParameters()
                                .get(RESOURCE_CONFIG, ResourceConfig.class))
                        .getConfigName()));

    if (createGkeDefaultSa(gcpProjectConfig)) {
      labelBuilder.put(GKE_DEFAULT_SA_LABEL_KEY, createValidLabelValue(GKE_SA_NAME));
    }

    getSecurityGroup(gcpProjectConfig)
        .ifPresent(str -> labelBuilder.put(SECURITY_GROUP_LABEL_KEY, createValidLabelValue(str)));

    return labelBuilder.build();
  }

  /**
   * Creates a valid GCP label value to meet the requirement. See <a
   * href='https://cloud.google.com/deployment-manager/docs/creating-managing-labels#requirements'>Requirements
   * for labels</a>
   */
  @VisibleForTesting
  public static String createValidLabelValue(String originalName) {
    String regex = "[^a-z0-9-_]+";
    String value = originalName.trim().toLowerCase().replaceAll(regex, "--");
    return value.length() > 64 ? value.substring(0, 63) : value;
  }

  /** Returns the uniquely identifying number of the project. */
  private static Long getNumber(Project project) {
    // The projects name has the form "projects/[project number]".
    return Long.parseLong(project.getName().substring("projects/".length()));
  }
}
