package scripts.testscripts;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BufferServiceUtils;
import scripts.utils.DataRepoUtils;
import scripts.utils.SAMUtils;

public class GetPoolInfo extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(GetPoolInfo.class);

  /** Public constructor so that this class can be instantiated via reflection. */
  public GetPoolInfo() {
    super();
  }

  public void userJourney(TestUserSpecification testUser) throws Exception {
    org.broadinstitute.dsde.workbench.client.sam.ApiClient samApiClient =
        SAMUtils.getClientForTestUser(testUser, server);

    bio.terra.datarepo.client.ApiClient datarepoApiClient =
        DataRepoUtils.getClientForTestUser(testUser, server);

    ApiClient apiClient = BufferServiceUtils.getClient(server);
    BufferApi bufferApi = new BufferApi(apiClient);
    PoolInfo poolInfo = bufferApi.getPoolInfo("123");
    logger.info("poolInfo: {}", poolInfo);

    int httpCode = bufferApi.getApiClient().getStatusCode();
    logger.info("GET pool info HTTP code: {}", httpCode);
  }
}
