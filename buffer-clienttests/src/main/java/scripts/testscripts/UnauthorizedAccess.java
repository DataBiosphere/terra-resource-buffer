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
import scripts.utils.BufferServiceUtils;

/** Verify invalid service account can not access Buffer Service.*/
public class UnauthorizedAccess extends TestScript {

  private static final Logger logger = LoggerFactory.getLogger(UnauthorizedAccess.class);

  /** Public constructor so that this class can be instantiated via reflection. */
  public UnauthorizedAccess() {
    super();
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = BufferServiceUtils.getClient(server);
    BufferApi bufferApi = new BufferApi(apiClient);
    try {
      retryHandout(bufferApi, UUID.randomUUID().toString());
      assertThat("Invalid SA account access not throwing exception", false);
    } catch (ApiException apiEx) {
      assertThat(apiEx.getCode(), equalTo(401));
    }
  }
}
