package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.*;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.pollUntilSuccess;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.projectIdToName;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.serviceusage.v1beta1.model.BatchEnableServicesRequest;
import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Enable services for project. */
public class EnableServicesStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(EnableServicesStep.class);
  private final ServiceUsageCow serviceUsageCow;
  private final GcpProjectConfig gcpProjectConfig;

  public EnableServicesStep(ServiceUsageCow serviceUsageCow, GcpProjectConfig gcpProjectConfig) {
    this.serviceUsageCow = serviceUsageCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    // Skip if enable apis is not set or empty.
    if (gcpProjectConfig.getEnabledApis() == null || gcpProjectConfig.getEnabledApis().isEmpty()) {
      return StepResult.getStepResultSuccess();
    }

    FlightMap workingMap = flightContext.getWorkingMap();
    String projectId = workingMap.get(GOOGLE_PROJECT_ID, String.class);
    if (projectId == null) {
      projectId = flightContext.getInputParameters().get(GOOGLE_PROJECT_ID, String.class);
    }
    try {
      OperationCow<?> operation =
          serviceUsageCow
              .operations()
              .operationCow(
                  serviceUsageCow
                      .services()
                      .batchEnable(
                          projectIdToName(projectId),
                          new BatchEnableServicesRequest()
                              .setServiceIds(gcpProjectConfig.getEnabledApis()))
                      .execute());
      pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
    }  catch (GoogleJsonResponseException e) {
      boolean canIgnoreError = e.getMessage() != null && e.getMessage().contains("is not available to this consumer");
      if (canIgnoreError) {
        logger.error("Skip enabling service(s): {}", e.getMessage());
      } else {
        logger.error("Error enabling services GCP project, id: {}", projectId, e);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error enabling services GCP project, id: {}", projectId, e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    // Set job response
    workingMap.put(DESCRIPTION, "Repaired project " + projectId);
    workingMap.put(STATUS_CODE, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
