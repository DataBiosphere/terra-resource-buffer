package scripts.utils;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a method to build an ApiClient object with the appropriate service account
 * credentials for a given Terra environment/deployment (i.e. ServerSpecification).
 *
 * <p>Any other utility methods that wrap client library functionality can also be added to this
 * class.
 */
public class BufferServiceUtils {

  private static final Logger logger = LoggerFactory.getLogger(BufferServiceUtils.class);

  /**
   * The pool id to get projects from. Pool config can be found at src/resources/config/perf folder
   * under Buffer Service repo.
   */
  public static final String POOL_ID = "resource_buffer_test_v1";

  /**
   * How ofter to poll from buffer service.
   */
  public static final Duration POLLING_INTERVAL = Duration.ofMinutes(1);

  private BufferServiceUtils() {
  }

  /**
   * Build the Buffer Service API client object for the given server specification.
   *
   * @param server the server we are testing against
   * @return the API client object
   */
  public static ApiClient getClient(ServerSpecification server) throws IOException {
    if (Strings.isNullOrEmpty(server.bufferUri)) {
      throw new IllegalArgumentException("Buffer Service URI cannot be empty");
    }
    if (server.bufferClientServiceAccount == null) {
      throw new IllegalArgumentException("Buffer Service client service account is required");
    }

    // refresh the client service account token
    GoogleCredentials serviceAccountCredential =
        AuthenticationUtils.getServiceAccountCredential(
            server.bufferClientServiceAccount, AuthenticationUtils.userLoginScopes);
    AccessToken accessToken = AuthenticationUtils.getAccessToken(serviceAccountCredential);
    logger.debug(
        "Generated access token for buffer service client SA: {}",
        server.bufferClientServiceAccount.name);

    // build the client object
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(server.bufferUri);
    apiClient.setAccessToken(accessToken.getTokenValue());

    return apiClient;
  }

  /**
   * Poll pool info from Buffer Service until READY resource is more than expect number.
   */
  public static PoolInfo pollUntilResourceCountExceed(
      ServerSpecification server, Duration timeout, int mimimumSize)
      throws InterruptedException, ApiException, IOException {
    Instant deadline = Instant.now().plus(timeout);
    int count = 0;
    while (true) {
      ApiClient apiClient = BufferServiceUtils.getClient(server);
      BufferApi bufferApi = new BufferApi(apiClient);
      count++;
      PoolInfo poolInfo = bufferApi.getPoolInfo(POOL_ID);
      logger.info("Total polling count: {}, poolInfo: {}", count, poolInfo);
      if (poolInfo.getResourceStateCount().get("READY") >= mimimumSize) {
        logger.info("Done after {} times poll. ", count);
        return poolInfo;
      }
      if (Instant.now().plus(POLLING_INTERVAL).isAfter(deadline)) {
        throw new InterruptedException("Timeout during pollUntilPoolFull");
      }
      Thread.sleep(POLLING_INTERVAL.toMillis());
    }
  }

  /**
   * Retries Handout resource API if resource is no resource is available.
   */
  public static String retryHandout(BufferApi bufferApi, String handoutRequestId)
      throws InterruptedException, ApiException {
    int numAttempts = 1;
    int maxNumAttempts = 5;
    while (numAttempts <= maxNumAttempts) {
      try {
        return bufferApi
            .handoutResource(new HandoutRequestBody().handoutRequestId(handoutRequestId), POOL_ID)
            .getCloudResourceUid()
            .getGoogleProjectUid()
            .getProjectId();
      } catch (ApiException e) {
        // Only retry when resource is not available (404).
        if (e.getCode() != 404) {
          throw e;
        }
        logger.info("No resource available, retrying... Attempts so far: {}", numAttempts, e);
      }
      ++numAttempts;
      TimeUnit.SECONDS.sleep(5);
    }
    throw new InterruptedException("Exceeds maximum number of retries.");
  }
}
