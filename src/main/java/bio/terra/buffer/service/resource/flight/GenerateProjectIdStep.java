package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator;
import bio.terra.stairway.*;

/** Generates Project Id and put it in working map. */
public class GenerateProjectIdStep implements Step {
  private final GcpProjectConfig gcpProjectConfig;
  private final GcpProjectIdGenerator projectIdGenerator;

  public GenerateProjectIdStep(
      GcpProjectConfig gcpProjectConfig, GcpProjectIdGenerator projectIdGenerator) {
    this.gcpProjectConfig = gcpProjectConfig;
    this.projectIdGenerator = projectIdGenerator;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    String projectId = projectIdGenerator.generateId(gcpProjectConfig.getProjectIdSchema());
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
}
