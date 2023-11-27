package bio.terra.buffer.common;

import static bio.terra.buffer.common.MetricsHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.ResourceConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Test for {@link bio.terra.cloudres.util.MetricsHelper} */
@Tag("unit")
public class MetricsHelperTest extends BaseUnitTest {
  private static final Duration METRICS_COLLECTION_INTERVAL = Duration.ofMillis(10);
  private MetricsHelper metricsHelper;
  private TestMetricExporter testMetricExporter;

  @BeforeEach
  void setup() {
    testMetricExporter = new TestMetricExporter();
    metricsHelper = new MetricsHelper(openTelemetry(testMetricExporter));
  }

  @Test
  public void testRecordResourceState() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .id(poolId)
            .size(10)
            .creation(BufferDao.currentInstant())
            .status(PoolStatus.ACTIVE)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .resourceConfig(new ResourceConfig().configName("configName"))
            .build();
    PoolAndResourceStates resourceStates =
        PoolAndResourceStates.builder()
            .setPool(pool)
            .setResourceStateCount(ResourceState.READY, 2)
            .setResourceStateCount(ResourceState.HANDED_OUT, 1)
            .build();

    PoolId poolId2 = PoolId.create("poolId2");
    Pool pool2 =
        Pool.builder()
            .id(poolId2)
            .size(10)
            .creation(BufferDao.currentInstant())
            .status(PoolStatus.DEACTIVATED)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .resourceConfig(new ResourceConfig().configName("configName"))
            .build();
    PoolAndResourceStates resourceStates2 =
        PoolAndResourceStates.builder()
            .setPool(pool2)
            .setResourceStateCount(ResourceState.HANDED_OUT, 10)
            .build();

    metricsHelper.recordResourceStateCount(resourceStates);
    metricsHelper.recordResourceStateCount(resourceStates2);
    var metricsByName =
        MetricsTestUtils.waitForMetrics(testMetricExporter, METRICS_COLLECTION_INTERVAL, 2).stream()
            .collect(Collectors.toMap(MetricData::getName, Function.identity()));
    ;
    assertEquals(
        Set.of(RESOURCE_STATE_COUNT_METER_NAME, READY_RESOURCE_RATIO_METER_NAME),
        metricsByName.keySet());

    // for each pool, check the count of each resource state and ready resource ratio
    Set.of(poolId, poolId2)
        .forEach(
            pid -> {
              Arrays.stream(ResourceState.values())
                  .forEach(
                      state -> {
                        var dataPoint =
                            (LongPointData)
                                metricsByName
                                    .get(RESOURCE_STATE_COUNT_METER_NAME)
                                    .getData()
                                    .getPoints()
                                    .stream()
                                    .filter(
                                        point -> {
                                          var attributes = point.getAttributes();
                                          return pid.id().equals(attributes.get(POOL_ID_KEY))
                                              && state
                                                  .toString()
                                                  .equals(attributes.get(RESOURCE_STATE_KEY));
                                        })
                                    .findFirst()
                                    .orElseThrow();
                        var expectedValue =
                            switch (state) {
                              case READY -> pid.equals(poolId) ? 2L : 0L;
                              case HANDED_OUT -> pid.equals(poolId) ? 1L : 10L;
                              default -> 0L;
                            };
                        assertEquals(expectedValue, dataPoint.getValue());
                      });

              var ratioPoint =
                  (DoublePointData)
                      metricsByName
                          .get(READY_RESOURCE_RATIO_METER_NAME)
                          .getData()
                          .getPoints()
                          .stream()
                          .filter(
                              point -> {
                                var attributes = point.getAttributes();
                                return pid.id().equals(attributes.get(POOL_ID_KEY));
                              })
                          .findFirst()
                          .orElseThrow();
              assertEquals(pid.equals(poolId) ? 0.2 : 1.0, ratioPoint.getValue());
            });
  }

  @Test
  public void testRecordHandoutResource() {
    PoolId poolId = PoolId.create("poolId");

    metricsHelper.recordHandoutResourceRequest(poolId);
    metricsHelper.recordHandoutResourceRequest(poolId);
    var metrics = MetricsTestUtils.waitForMetrics(testMetricExporter, METRICS_COLLECTION_INTERVAL);
    assertEquals(HANDOUT_RESOURCE_REQUEST_COUNT_METER_NAME, metrics.getName());
    var currentCount = ((LongPointData) metrics.getData().getPoints().iterator().next()).getValue();
    assertEquals(2, currentCount);
  }

  public OpenTelemetry openTelemetry(TestMetricExporter testMetricExporter) {
    var sdkMeterProviderBuilder =
        SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(testMetricExporter)
                    .setInterval(METRICS_COLLECTION_INTERVAL)
                    .build());

    return OpenTelemetrySdk.builder().setMeterProvider(sdkMeterProviderBuilder.build()).build();
  }
}
