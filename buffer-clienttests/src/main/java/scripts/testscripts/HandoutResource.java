package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static scripts.utils.BufferServiceUtils.POOL_ID;
import static scripts.utils.BufferServiceUtils.pollUntilPoolFull;
import static scripts.utils.BufferServiceUtils.retryHandout;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BufferServiceUtils;

/**
 * A {@link TestScript} which request X projects from Buffer Services then verify pool can be
 * refilled after Y hours.
 */
public class HandoutResource extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(HandoutResource.class);

  private int poolSize;
  private static int successCount;

  /** Public constructor so that this class can be instantiated via reflection. */
  public HandoutResource() {
    super();
  }

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    // Verify pool is full before the test. If pool is not empty, poll until it is full.
    ApiClient apiClient = BufferServiceUtils.getClient(server);
    BufferApi bufferApi = new BufferApi(apiClient);
    poolSize = bufferApi.getPoolInfo(POOL_ID).getPoolConfig().getSize();
    pollUntilPoolFull(server, Duration.ofMinutes(30), poolSize);
    assertThat(
        bufferApi.getPoolInfo(POOL_ID).getResourceStateCount().get("READY"), equalTo(poolSize));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = BufferServiceUtils.getClient(server);
    BufferApi bufferApi = new BufferApi(apiClient);
    String handoutRequestId = UUID.randomUUID().toString();
    logger.info("Generated handoutRequestId: {}", handoutRequestId);
    String projectId = retryHandout(bufferApi, handoutRequestId);
    successCount++;
    logger.info("project Id: {} for handoutRequestId: {}", projectId, handoutRequestId);
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    logger.info("Success count: {}", successCount);
    // For now we verifies all RESOURCE_COUNT calls successfully. Not sure that is too ideal or we
    // want to set some threshold like we allow 0.1% failure rate is allowable in this burst case.
    PoolInfo poolInfo = pollUntilPoolFull(server, Duration.ofHours(2), poolSize);
    assertThat(poolInfo.getResourceStateCount().get("CREATING"), equalTo(0));
    assertThat(poolInfo.getResourceStateCount().get("READY"), equalTo(poolSize));
  }
}
