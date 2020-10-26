package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/** Utilities when use Google APIs. */
public class GoogleUtils {
  /** All project will use the same network name. */
  @VisibleForTesting public static final String NETWORK_NAME = "network";

  /**
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }

  /** Converts project id to name. */
  public static String projectIdToName(String projectId) {
    return "projects/" + projectId;
  }

  /** Checks if network monitoring is enabled from config. */
  public static boolean checkEnableNetworkMonitoring(GcpProjectConfig gcpProjectConfig) {
    return gcpProjectConfig.getNetwork() != null
        && gcpProjectConfig.getNetwork().isEnableNetworkMonitoring() != null
        && gcpProjectConfig.getNetwork().isEnableNetworkMonitoring();
  }

  /**
   * Checks if cloudObject already exists.
   *
   * <p>Many Google 'get' operations return a {@link GoogleJsonResponseException} 404. This function
   * handles that exception case as false, while throwing any other exception.
   */
  public static <R> boolean resourceExists(CowExecute<R> execute) throws IOException {
    return getResource(execute).isPresent();
  }

  public static <R> Optional<R> getResource(CowExecute<R> execute) throws IOException {
    try {
      return Optional.of(execute.execute());
    } catch (IOException e) {
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getStatusCode() == 404) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  /** Wrappers how to process a CRL's Cow execution. */
  @FunctionalInterface
  public interface CowExecute<R> {
    R execute() throws IOException;
  }
}
