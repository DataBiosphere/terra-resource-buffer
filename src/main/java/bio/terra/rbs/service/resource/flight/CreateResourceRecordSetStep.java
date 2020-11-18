package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;

import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
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
  public static final ResourceRecordSet A_RECORD =
      new ResourceRecordSet()
          .setType("A")
          .setName("restricted.googleapis.com.")
          .setRrdatas(
              ImmutableList.of("199.36.153.4", "199.36.153.5", "199.36.153.6", "199.36.153.7"))
          .setTtl(300);

  @VisibleForTesting
  public static final ResourceRecordSet CNAME_RECORD =
      new ResourceRecordSet()
          .setType("CNAME")
          .setName("*.googleapis.com.")
          .setRrdatas(ImmutableList.of("restricted.googleapis.com."))
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
    if (!isNetworkMonitoringEnabled(gcpProjectConfig)) {
      return StepResult.getStepResultSuccess();
    }
    try {
      // ManagedZone is already create in previous step so it won't be empty here.
      // If we got NPE, that means something went wrong in previous step, fine to just throw NPE
      // here.
      ManagedZone managedZone =
          getResource(() -> dnsCow.managedZones().get(projectId, MANAGED_ZONE_NAME).execute(), 404)
              .get();

      // Find all ResourceRecordSets to check if A and CNAME already created.
      Map<String, ResourceRecordSet> resourceRecordSetMap =
          dnsCow
              .resourceRecordSets()
              .list(projectId, MANAGED_ZONE_NAME)
              .execute()
              .getRrsets()
              .stream()
              .collect(Collectors.toMap(ResourceRecordSet::getType, r -> r));
      List<ResourceRecordSet> resourceRecordSetsToCreate = new ArrayList<>();
      if (!resourceRecordSetMap.containsKey(A_RECORD.getType())) {
        resourceRecordSetsToCreate.add(A_RECORD);
      }
      if (!resourceRecordSetMap.containsKey(CNAME_RECORD.getType())) {
        resourceRecordSetsToCreate.add(CNAME_RECORD);
      }
      dnsCow
          .changes()
          .create(
              projectId,
              managedZone.getName(),
              new Change().setAdditions(resourceRecordSetsToCreate))
          .execute();

    } catch (IOException e) {
      logger.info("Error when configuring ResourceRecordSets ", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks ResourceRecordSets exists or not. So no need to delete
    // ResourceRecordSets.
    return StepResult.getStepResultSuccess();
  }
}
