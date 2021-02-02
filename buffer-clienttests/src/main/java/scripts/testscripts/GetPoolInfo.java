package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BufferServiceUtils;

public class GetPoolInfo extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(GetPoolInfo.class);

  /** Public constructor so that this class can be instantiated via reflection. */
  public GetPoolInfo() {
    super();
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = BufferServiceUtils.getClient(server);
    BufferApi bufferApi = new BufferApi(apiClient);

    // TODO: is there a valid pool id that I could use here instead of expecting failure?
    try {
      PoolInfo poolInfo = bufferApi.getPoolInfo("123");
      logger.debug("poolInfo: {}", poolInfo);
      assertThat("GET pool info did not throw not found exception", false);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception fetching pool info", apiEx);
      assertThat("Exception text reason", apiEx.getResponseBody().contains("Pool 123 not found"));
    }

    int httpCode = bufferApi.getApiClient().getStatusCode();
    logger.info("GET pool info HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(404));
  }
}
