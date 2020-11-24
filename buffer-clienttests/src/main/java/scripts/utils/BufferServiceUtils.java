package scripts.utils;

import bio.terra.buffer.client.ApiClient;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferServiceUtils {
  private static final Logger logger = LoggerFactory.getLogger(BufferServiceUtils.class);

  private BufferServiceUtils() {}

  /**
   * Build the Buffer Service API client object for the given server specification.
   *
   * @param server the server we are testing against
   * @return the API client object
   */
  public static ApiClient getClient(ServerSpecification server) throws IOException {
    if (server.bufferUri == null || server.bufferUri.isEmpty()) {
      throw new IllegalArgumentException("Buffer Service URI cannot be empty");
    }
    if (server.bufferClientServiceAccount == null) {
      throw new IllegalArgumentException("Buffer Service client service account is required");
    }

    // refresh the client service account token
    GoogleCredentials clientCredential =
        AuthenticationUtils.getServiceAccountCredential(server.bufferClientServiceAccount);
    AccessToken clientAccessToken = AuthenticationUtils.getAccessToken(clientCredential);
    logger.info(
        "Generated access token for buffer service client SA: {}",
        server.bufferClientServiceAccount.name);

    // build the client object
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(server.bufferUri);
    apiClient.setAccessToken(clientAccessToken.getTokenValue());

    return apiClient;
  }
}
