package scripts.testscripts;

import bio.terra.buffer.api.UnauthenticatedApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.model.SystemStatus;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BufferServiceUtils;

public class ServiceStatus extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(ServiceStatus.class);

  /** Public constructor so that this class can be instantiated via reflection. */
  public ServiceStatus() {
    super();
  }

  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = BufferServiceUtils.getClient(server);
    UnauthenticatedApi unauthenticatedApi = new UnauthenticatedApi(apiClient);
    SystemStatus systemStatus = unauthenticatedApi.serviceStatus();
    logger.info("systemStatus: {}", systemStatus);

    int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
    logger.info("Service status HTTP code: {}", httpCode);
  }
}
