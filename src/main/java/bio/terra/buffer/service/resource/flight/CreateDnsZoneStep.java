package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.enableGcrPrivateGoogleAccess;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.usePrivateGoogleAccess;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.GCR_MANAGED_ZONE_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.MANAGED_ZONE_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.createResourceAndIgnoreConflict;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.getResource;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.dns.model.ManagedZone;
import com.google.api.services.dns.model.ManagedZonePrivateVisibilityConfig;
import com.google.api.services.dns.model.ManagedZonePrivateVisibilityConfigNetwork;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configs DNS zones. This Step with the following CreateResourceRecordSetStep together make Google
 * domain names resolve to the set of IP addresses for either private.googleapis.com or
 * restricted.googleapis.com.
 *
 * <p>Follow the same steps and values from here:
 * https://cloud.google.com/vpc-service-controls/docs/set-up-private-connectivity#configuring_dns_with
 *
 * <p>When enableGcrPrivateGoogleAccess, follow the similar step to create DNS zone for gcr.io
 */
public class CreateDnsZoneStep implements Step {
  @VisibleForTesting
  public static final ManagedZone MANAGED_ZONE_TEMPLATE =
      new ManagedZone()
          .setName(MANAGED_ZONE_NAME)
          .setDescription("Routes googleapis.com to restricted.googleapis.com VIP")
          .setDnsName("googleapis.com.")
          .setVisibility("private");

  @VisibleForTesting
  public static final ManagedZone GCR_MANAGED_ZONE_TEMPLATE =
      new ManagedZone()
          .setName(GCR_MANAGED_ZONE_NAME)
          .setDescription("Routes gcr.io to restricted.googleapis.com")
          .setDnsName("gcr.io.")
          .setVisibility("private");

  private final Logger logger = LoggerFactory.getLogger(CreateDnsZoneStep.class);
  private final CloudComputeCow computeCow;
  private final DnsCow dnsCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateDnsZoneStep(
      CloudComputeCow computeCow, DnsCow dnsCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.dnsCow = dnsCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    if (!usePrivateGoogleAccess(gcpProjectConfig)) {
      return StepResult.getStepResultSuccess();
    }
    try {
      // Network is already created and checked in previous step so here won't be empty.
      // If we got NPE, that means something went wrong with GCP, fine to just throw NPE here.
      Network network =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();

      createManagedDnsZone(projectId, network, MANAGED_ZONE_TEMPLATE);

      if (enableGcrPrivateGoogleAccess(gcpProjectConfig)) {
        createManagedDnsZone(projectId, network, GCR_MANAGED_ZONE_TEMPLATE);
      }

    } catch (IOException e) {
      logger.info("Error when configuring DNS ", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private void createManagedDnsZone(
      String projectId, Network network, ManagedZone managedZoneTemplatae) throws IOException {
    // Skip ManagedZone creation if ManagedZone already present.
    ManagedZone managedZone =
        managedZoneTemplatae
            .clone()
            .setPrivateVisibilityConfig(
                new ManagedZonePrivateVisibilityConfig()
                    .setNetworks(
                        ImmutableList.of(
                            new ManagedZonePrivateVisibilityConfigNetwork()
                                .setNetworkUrl(network.getSelfLink()))));

    createResourceAndIgnoreConflict(
        () -> dnsCow.managedZones().create(projectId, managedZone).execute());
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks DNS zone exists or not. So no need to delete the zone.
    return StepResult.getStepResultSuccess();
  }
}
