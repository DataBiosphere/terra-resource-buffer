package scripts.testscripts;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.CloudResourceUid;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.BufferServiceUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static scripts.utils.BufferServiceUtils.*;

/** A {@link TestScript} which request X projects from Buffer Services then verify pool can be refilled after Y hours.*/
public class HandoutResource extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(HandoutResource.class);

  /** How many projects to get for from Buffer Service. */
  private static final Integer RESOURCE_COUNT = 1000;

  /** Public constructor so that this class can be instantiated via reflection. */
  public HandoutResource() {
    super();
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = BufferServiceUtils.getClient(server);
    BufferApi bufferApi = new BufferApi(apiClient);
    Set<String> handoutRequestIds = new HashSet<>();
    for(int i = 0; i < RESOURCE_COUNT; i ++) {
      String handoutRequestId = UUID.randomUUID().toString();
      logger.debug("Generated handoutRequestId: {}", handoutRequestId);
      try {
      String projectId = bufferApi.handoutResource(POOL_ID, handoutRequestId).getCloudResourceUid().getGoogleProjectUid().getProjectId();
      logger.debug("Iteration: {}, project Id: {} for handoutRequestId: {}", i, projectId, handoutRequestId);
      assertThat(handoutRequestIds, not(hasItem(projectId)));
      handoutRequestIds.add(projectId);
      } catch (ApiException apiEx) {
        logger.debug("Caught exception requesting resource, handoutRequestId: {}", handoutRequestId, apiEx);
      }
    }
    logger.debug("Done requesting {} number of resources", handoutRequestIds.size());
    // For now we verifies all RESOURCE_COUNT calls successfully. Not sure that is too ideal or we want to set some
    // threshold like we allow 0.1% failure rate is allowable in this burst case.
    assertThat(handoutRequestIds.size(), equalTo(RESOURCE_COUNT));
    PoolInfo poolInfo = pollUntilPoolFull(bufferApi, Duration.ofHours(2));
    assertThat(poolInfo.getResourceStateCount().get("CREATING"), equalTo(0));
    assertThat(poolInfo.getResourceStateCount().get("READY"), equalTo(POOL_SIZE));
  }
}
