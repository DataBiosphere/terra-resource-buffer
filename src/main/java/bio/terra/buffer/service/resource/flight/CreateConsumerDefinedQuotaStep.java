package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_NUMBER;

import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.serviceusage.v1beta1.model.Operation;
import com.google.api.services.serviceusage.v1beta1.model.QuotaOverride;
import java.io.IOException;
import java.util.Collections;

public class CreateConsumerDefinedQuotaStep implements Step {
  private final ServiceUsageCow serviceUsageCow;

  public CreateConsumerDefinedQuotaStep(ServiceUsageCow serviceUsageCow) {
    this.serviceUsageCow = serviceUsageCow;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Long projectNumber = context.getWorkingMap().get(GOOGLE_PROJECT_NUMBER, Long.class);
    String projectName = context.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    QuotaOverride overridePerProjectPerDay = buildQuotaOverride(projectNumber);

    try {
      Operation createOp =
          serviceUsageCow
              .services()
              .consumerQuotaMetrics()
              .limits()
              .consumerOverrides()
              .create(projectName, overridePerProjectPerDay)
              .execute();
    } catch (IOException e) {
      throw new RetryException(e);
    }
    return StepResult.getStepResultSuccess();
  }

  private QuotaOverride buildQuotaOverride(Long projectNumber) {
    var overridePerProjectPerDay = new QuotaOverride();
    String METRIC_NAME = "bigquery.googleapis.com/quota/query/usage";
    overridePerProjectPerDay.setMetric(METRIC_NAME);
    // fill in the project number for the quota limit name
    String PER_PROJECT_PER_DAY_CONSUMER_QUOTA_LIMIT_NAME_FORMAT =
        "projects/%d/services/bigquery.googleapis.com/consumerQuotaMetrics/"
            + "bigquery.googleapis.com%%2Fquota%%2Fquery%%2Fusage/limits/%%2Fd%%2Fproject";
    overridePerProjectPerDay.setName(
        String.format(PER_PROJECT_PER_DAY_CONSUMER_QUOTA_LIMIT_NAME_FORMAT, projectNumber));
    long TERABYTE_IN_BYTES = 1_099_511_627_776L;
    long PER_PROJECT_PER_DAY_QUERY_USAGE_OVERRIDE_BYTES = 40 * TERABYTE_IN_BYTES;
    overridePerProjectPerDay.setOverrideValue(PER_PROJECT_PER_DAY_QUERY_USAGE_OVERRIDE_BYTES);
    overridePerProjectPerDay.setDimensions(Collections.emptyMap());
    overridePerProjectPerDay.setUnit("1/d/{project}");
    overridePerProjectPerDay.setAdminOverrideAncestor(null);
    return overridePerProjectPerDay;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
