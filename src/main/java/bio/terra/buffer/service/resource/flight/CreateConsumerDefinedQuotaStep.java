package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_NUMBER;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.pollUntilSuccess;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.serviceusage.v1beta1.model.Operation;
import com.google.api.services.serviceusage.v1beta1.model.QuotaOverride;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateConsumerDefinedQuotaStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateConsumerDefinedQuotaStep.class);
  private final ServiceUsageCow serviceUsageCow;

  public CreateConsumerDefinedQuotaStep(ServiceUsageCow serviceUsageCow) {
    this.serviceUsageCow = serviceUsageCow;
  }

  /** Apply a Consumer Quota Override for the BigQuery Query Usage Quota. */
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Long projectNumber = context.getWorkingMap().get(GOOGLE_PROJECT_NUMBER, Long.class);

    QuotaOverride overridePerProjectPerDay = buildQuotaOverride(projectNumber);
    // parent format and other details obtained by hitting the endpoint
    // https://serviceusage.googleapis.com/v1beta1/projects/${PROJECT_NUMBER}/services/bigquery.googleapis.com/consumerQuotaMetrics
    String parent =
        String.format(
            "projects/%d/services/bigquery.googleapis.com/consumerQuotaMetrics/"
                + "bigquery.googleapis.com%%2Fquota%%2Fquery%%2Fusage/limits/%%2Fd%%2Fproject",
            projectNumber);
    try {
      // We are decreasing the quota by more than 10%, so we must tell Service Usage to bypass the
      // check with the force flag.
      Operation createOperation =
          serviceUsageCow
              .services()
              .consumerQuotaMetrics()
              .limits()
              .consumerOverrides()
              .create(parent, overridePerProjectPerDay)
              .setForce(true)
              .execute();
      OperationCow<Operation> operationCow =
          serviceUsageCow.operations().operationCow(createOperation);
      pollUntilSuccess(operationCow, Duration.ofSeconds(3), Duration.ofMinutes(5));
    } catch (IOException e) {
      throw new RetryException(e);
    }
    return StepResult.getStepResultSuccess();
  }

  private QuotaOverride buildQuotaOverride(Long projectNumber) {
    var result = new QuotaOverride();
    result.setMetric("bigquery.googleapis.com/quota/query/usage");
    // fill in the project number for the quota limit name
    result.setName(
        String.format(
            "projects/%d/services/bigquery.googleapis.com/"
                + "consumerQuotaMetrics/bigquery.googleapis.com%%2Fquota%%2Fquery%%2Fusage",
            projectNumber));
    long TERABYTE_IN_BYTES = 1_099_511_627_776L;
    result.setOverrideValue(40 * TERABYTE_IN_BYTES);
    result.setDimensions(Collections.emptyMap());
    result.setUnit("1/d/{project}"); // no substitution - literal {}s
    result.setAdminOverrideAncestor(null);
    return result;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
