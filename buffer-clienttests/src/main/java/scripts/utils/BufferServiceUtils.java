package scripts.utils;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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
  public static final String POOL_ID = "resource_toolsalpha_v1";

  /**
   * The size of the pool. Pool config also can be found at src/resources/config/perf folder under
   * BufferService repo.
   */
  public static final Integer POOL_SIZE = 3;

  private BufferServiceUtils() {}

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
    apiClient.setDebugging(true);

    return apiClient;
  }

  /**
   * Poll poll info from Buffer Service until the pool is full. Throws any error or timeouts as a
   * {@link InterruptedException}.
   */
  public static PoolInfo pollUntilPoolFull(BufferApi bufferApi, Duration timeout)
      throws InterruptedException, ApiException {
    Instant deadline = Instant.now().plus(timeout);
    int count = 1;

    while (true) {
      PoolInfo poolInfo = bufferApi.getPoolInfo(POOL_ID);
      logger.debug("Total polling count: {}, poolInfo: {}", count, poolInfo);
      if (poolInfo.getResourceStateCount().get("READY").equals(POOL_SIZE)) {
        logger.debug("Done after {} times poll. ", count);
        return poolInfo;
      }
      if (Instant.now().plus(Duration.ofMinutes(3)).isAfter(deadline)) {
        throw new InterruptedException("Timeout during pollUntilPoolFull");
      }
    }
  }
}
