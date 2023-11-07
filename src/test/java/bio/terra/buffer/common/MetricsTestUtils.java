package bio.terra.buffer.common;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.stairway.MetricsHelper;
import bio.terra.stairway.Direction;
import bio.terra.stairway.FlightStatus;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class MetricsTestUtils {
  public static MetricData waitForMetrics(
      TestMetricExporter testMetricExporter, Duration pollInterval) {
    return waitForMetrics(testMetricExporter, pollInterval, 1).iterator().next();
  }

  public static Collection<MetricData> waitForMetrics(
      TestMetricExporter testMetricExporter, Duration pollInterval, int expectedMetricsCount) {
    await()
        .atMost(pollInterval.multipliedBy(10))
        .pollInterval(pollInterval)
        .until(
            () ->
                testMetricExporter.getLastMetrics() != null
                    && testMetricExporter.getLastMetrics().size() == expectedMetricsCount);
    return testMetricExporter.getLastMetrics();
  }

  public static void assertFlightErrorMeterValues(
      MetricData metric, Map<FlightStatus, Long> expected) {
    var valuesByError =
        metric.getData().getPoints().stream()
            .collect(
                Collectors.toMap(
                    point ->
                        FlightStatus.valueOf(
                            point
                                .getAttributes()
                                .get(bio.terra.common.stairway.MetricsHelper.KEY_ERROR)),
                    point -> ((LongPointData) point).getValue()));

    assertEquals(expected, valuesByError);
  }

  public static void assertStepErrorMeterValues(
      MetricData metric, String stepName, Map<Direction, Long> expected) {
    var valuesByStepDirection =
        metric.getData().getPoints().stream()
            .filter(
                point ->
                    stepName.equals(
                        point
                            .getAttributes()
                            .get(bio.terra.common.stairway.MetricsHelper.KEY_STEP_NAME)))
            .collect(
                Collectors.toMap(
                    point ->
                        Direction.valueOf(
                            point
                                .getAttributes()
                                .get(bio.terra.common.stairway.MetricsHelper.KEY_STEP_DIRECTION)),
                    point -> ((LongPointData) point).getValue()));

    assertEquals(expected, valuesByStepDirection);
  }

  public static void assertLatencyBucketCounts(
      MetricData metric, Map<Integer, Long> expectedCountsByBucketIndex) {
    assertEquals(1, metric.getData().getPoints().size());
    var point = (HistogramPointData) metric.getData().getPoints().iterator().next();
    expectedCountsByBucketIndex.forEach(
        (bucketIndex, expectedCount) ->
            assertEquals(expectedCount, point.getCounts().get(bucketIndex)));
  }

  public static void assertLatencyTotalCount(MetricData metric, Long expectedTotalCount) {
    assertEquals(1, metric.getData().getPoints().size());
    var point = (HistogramPointData) metric.getData().getPoints().iterator().next();
    assertEquals(expectedTotalCount, point.getCount());
  }

  public static void assertStepLatencyTotalCount(
      MetricData metric, String stepName, Direction direction, Long expectedTotalCount) {
    var maybePoint =
        metric.getData().getPoints().stream()
            .filter(
                point ->
                    stepName.equals(
                            point
                                .getAttributes()
                                .get(bio.terra.common.stairway.MetricsHelper.KEY_STEP_NAME))
                        && direction.equals(
                            Direction.valueOf(
                                point.getAttributes().get(MetricsHelper.KEY_STEP_DIRECTION))))
            .findFirst();
    assertTrue(maybePoint.isPresent());
    var histogramPointData = (HistogramPointData) maybePoint.get();
    assertEquals(expectedTotalCount, histogramPointData.getCount());
  }
}
