package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static scripts.utils.BufferServiceUtils.retryHandout;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TestScript} which
 * refilled after Y hours.
 */
public class UnauthorizedHandoutResource extends TestScript {

  private static final Logger logger = LoggerFactory.getLogger(UnauthorizedHandoutResource.class);

  /** Public constructor so that this class can be instantiated via reflection. */
  public UnauthorizedHandoutResource() {
    super();
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(server.bufferUri);
    BufferApi bufferApi = new BufferApi(apiClient);
    String handoutRequestId = UUID.randomUUID().toString();
    logger.info("Generated handoutRequestId: {}", handoutRequestId);
    try {
      retryHandout(bufferApi, handoutRequestId);
      assertThat("GET pool info did not throw not found exception", false);
    } catch (ApiException apiEx) {
      assertThat(apiEx.getCode(), equalTo(401));
    }
  }
}
