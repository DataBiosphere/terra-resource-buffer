package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GoogleProjectUid;
import bio.terra.stairway.*;
import java.util.UUID;

/** Generates Project Id and put it in working map. */
public class GenerateProjectIdStep implements Step {
  public GenerateProjectIdStep() {}

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    // TODO(PF-168): Use Terra Project Id generator.
    String projectId = randomProjectId();
    flightContext.getWorkingMap().put(GOOGLE_PROJECT_ID, projectId);
    workingMap.put(
        CLOUD_RESOURCE_UID,
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId)));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  public static String randomProjectId() {
    // TODO: Replace with name schema once that is finalized.
    return "p" + UUID.randomUUID().toString().substring(0, 29);
  }
}
