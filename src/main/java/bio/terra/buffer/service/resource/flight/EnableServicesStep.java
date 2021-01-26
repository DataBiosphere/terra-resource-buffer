package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.app.configuration.CrlConfiguration.CLIENT_NAME;
import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.serviceusage.v1.ServiceUsage;
import com.google.api.services.serviceusage.v1.model.BatchEnableServicesRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Enable services for project. */
public class EnableServicesStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(EnableServicesStep.class);
  private final GcpProjectConfig gcpProjectConfig;
  private final ClientConfig clientConfig;

  public EnableServicesStep(GcpProjectConfig gcpProjectConfig, ClientConfig clientConfig) {
    this.gcpProjectConfig = gcpProjectConfig;
    this.clientConfig = clientConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      ServiceUsageCow serviceUsageCow =
          new ServiceUsageCow(
              clientConfig,
              new ServiceUsage.Builder(
                      GoogleNetHttpTransport.newTrustedTransport(),
                      Defaults.jsonFactory(),
                      setUserProject(
                          new HttpCredentialsAdapter(
                              GoogleCredentials.getApplicationDefault()
                                  .createScoped(ComputeScopes.all())),
                          projectId))
                  .setApplicationName(CLIENT_NAME));

      // Skip if enable apis is not set or empty.
      if (gcpProjectConfig.getEnabledApis() == null
          || gcpProjectConfig.getEnabledApis().isEmpty()) {
        return StepResult.getStepResultSuccess();
      }
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
    } catch (IOException | InterruptedException | GeneralSecurityException e) {
      logger.info("Error enabling services GCP project, id: {}", projectId, e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
