package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.enableGcrPrivateGoogleAccess;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.usePrivateGoogleAccess;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.GCR_MANAGED_ZONE_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.MANAGED_ZONE_NAME;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.getResource;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.dns.model.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configs record set for DNS. See {@link CreateDnsZoneStep} */
public class CreateResourceRecordSetStep implements Step {
  @VisibleForTesting
  public static final ResourceRecordSet RESTRICT_API_A_RECORD =
      new ResourceRecordSet()
          .setType("A")
          .setName("restricted.googleapis.com.")
          .setRrdatas(
              ImmutableList.of("199.36.153.4", "199.36.153.5", "199.36.153.6", "199.36.153.7"))
          .setTtl(300);

  @VisibleForTesting
  public static final ResourceRecordSet RESTRICT_API_CNAME_RECORD =
      new ResourceRecordSet()
          .setType("CNAME")
          .setName("*.googleapis.com.")
          .setRrdatas(ImmutableList.of("restricted.googleapis.com."))
          .setTtl(300);

  @VisibleForTesting
  public static final ResourceRecordSet GCR_A_RECORD =
      new ResourceRecordSet()
          .setType("A")
          .setName("gcr.io.")
          .setRrdatas(
              ImmutableList.of("199.36.153.4", "199.36.153.5", "199.36.153.6", "199.36.153.7"))
          .setTtl(300);

  @VisibleForTesting
  public static final ResourceRecordSet GCR_CNAME_RECORD =
      new ResourceRecordSet()
          .setType("CNAME")
          .setName("*.gcr.io.")
          .setRrdatas(ImmutableList.of("gcr.io."))
          .setTtl(300);

  private final Logger logger = LoggerFactory.getLogger(CreateResourceRecordSetStep.class);
  private final DnsCow dnsCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateResourceRecordSetStep(DnsCow dnsCow, GcpProjectConfig gcpProjectConfig) {
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
      createRecordSetForDnsZone(
          projectId,
          MANAGED_ZONE_NAME,
          ImmutableList.of(RESTRICT_API_A_RECORD, RESTRICT_API_CNAME_RECORD));

      if (enableGcrPrivateGoogleAccess(gcpProjectConfig)) {
        createRecordSetForDnsZone(
            projectId, GCR_MANAGED_ZONE_NAME, ImmutableList.of(GCR_A_RECORD, GCR_CNAME_RECORD));
      }
    } catch (IOException e) {
      logger.info("Error when configuring ResourceRecordSets ", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  private void createRecordSetForDnsZone(
      String projectId, String zoneName, List<ResourceRecordSet> resourceRecordSets)
      throws IOException {
    // ManagedZone is already create in previous step so it won't be empty here.
    // If we got NPE, that means something went wrong in previous step, fine to just throw NPE
    // here.
    ManagedZone managedZone =
        getResource(() -> dnsCow.managedZones().get(projectId, zoneName).execute(), 404).get();

    // Find all ResourceRecordSets to check if A and CNAME already created.
    Map<String, ResourceRecordSet> resourceRecordSetMap =
        dnsCow.resourceRecordSets().list(projectId, zoneName).execute().getRrsets().stream()
            .collect(Collectors.toMap(ResourceRecordSet::getType, r -> r));
    List<ResourceRecordSet> resourceRecordSetsToCreate = new ArrayList<>();

    for (ResourceRecordSet resourceRecordSet : resourceRecordSets) {
      if (!resourceRecordSetMap.containsKey(resourceRecordSet.getType())) {
        resourceRecordSetsToCreate.add(resourceRecordSet);
      }
    }

    dnsCow
        .changes()
        .create(
            projectId, managedZone.getName(), new Change().setAdditions(resourceRecordSetsToCreate))
        .execute();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks ResourceRecordSets exists or not. So no need to delete
    // ResourceRecordSets.
    return StepResult.getStepResultSuccess();
  }
}
