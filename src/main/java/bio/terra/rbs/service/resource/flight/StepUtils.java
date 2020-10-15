package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.stairway.exception.RetryException;
import java.io.IOException;
import java.time.Duration;

/** Utilities used in Stairway steps. */
public class StepUtils {
  /**
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation =
        OperationUtils.pollUntilComplete(operation, Duration.ofSeconds(20), Duration.ofMinutes(5));
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }
}
