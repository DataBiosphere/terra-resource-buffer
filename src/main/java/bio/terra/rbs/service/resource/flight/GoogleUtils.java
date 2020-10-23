package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.time.Duration;

/** Utilities when use Google APIs. */
public class GoogleUtils {
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

  /** Checks if cloudObject already exists. */
  public static boolean cloudObjectExists(CowExecute execute) throws IOException {
    try {
      execute.execute();
      return true;
    } catch (IOException e) {
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getStatusCode() == 404) {
        return false;
      } else {
        throw e;
      }
    }
  }

  public interface CowExecute {
    void execute() throws IOException;
  }
}
