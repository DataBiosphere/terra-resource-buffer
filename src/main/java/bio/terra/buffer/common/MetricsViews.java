package bio.terra.buffer.common;

import static bio.terra.buffer.common.MetricsHelper.POOL_ID_KEY;
import static bio.terra.buffer.common.MetricsHelper.POOL_STATUS_KEY;
import static bio.terra.buffer.common.MetricsHelper.RESOURCE_STATE_KEY;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.util.Pair;

@Configuration
public class MetricsViews {
  @Bean(name = MetricsHelper.RESOURCE_STATE_COUNT_METER_NAME)
  public Pair<InstrumentSelector, View> resourceStateCountView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.RESOURCE_STATE_COUNT_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.RESOURCE_STATE_COUNT_METER_NAME)
            .setDescription("Counts resource number by state")
            .setAggregation(Aggregation.lastValue())
            .setAttributeFilter(
                Set.of(RESOURCE_STATE_KEY.getKey(), POOL_ID_KEY.getKey(), POOL_STATUS_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.READY_RESOURCE_RATIO_METER_NAME)
  public Pair<InstrumentSelector, View> readyResourceRatioView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.READY_RESOURCE_RATIO_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.READY_RESOURCE_RATIO_METER_NAME)
            .setDescription("The ratio of ready resource count to pool size ratio, in #.## format")
            .setAggregation(Aggregation.lastValue())
            .setAttributeFilter(Set.of(POOL_ID_KEY.getKey()))
            .build());
  }

  @Bean(name = MetricsHelper.HANDOUT_RESOURCE_REQUEST_COUNT_METER_NAME)
  public Pair<InstrumentSelector, View> handoutResourceRequestCountView() {
    return Pair.of(
        InstrumentSelector.builder()
            .setMeterName(MetricsHelper.HANDOUT_RESOURCE_REQUEST_COUNT_METER_NAME)
            .build(),
        View.builder()
            .setName(MetricsHelper.HANDOUT_RESOURCE_REQUEST_COUNT_METER_NAME)
            .setDescription("Counts resource handed out.")
            .setAggregation(Aggregation.sum())
            .setAttributeFilter(Set.of(POOL_ID_KEY.getKey()))
            .build());
  }
}
