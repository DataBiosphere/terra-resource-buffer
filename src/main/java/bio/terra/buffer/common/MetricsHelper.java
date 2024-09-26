package bio.terra.buffer.common;

import com.google.common.collect.Multiset;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Helper class for recording metrics associated in Resource Buffer Service. */
@Component
public class MetricsHelper implements AutoCloseable {

  public static final String PREFIX = "terra/buffer/resource";
  public static final String HANDOUT_RESOURCE_REQUEST_COUNT_METER_NAME =
      PREFIX + "/handout_resource_request_count";
  public static final String READY_RESOURCE_RATIO_METER_NAME = PREFIX + "/ready_resource_ratio";
  public static final String RESOURCE_STATE_COUNT_METER_NAME = PREFIX + "/resource_state_count";

  public static final AttributeKey<String> RESOURCE_STATE_KEY =
      AttributeKey.stringKey("resource_state");
  public static final AttributeKey<String> POOL_ID_KEY = AttributeKey.stringKey("pool_id");
  public static final AttributeKey<String> POOL_STATUS_KEY = AttributeKey.stringKey("pool_status");

  /** Unit string for count. */
  private static final String COUNT = "1";

  /** Unit string for resource count to pool size ratio. */
  private static final String RESOURCE_TO_POOL_SIZE_RATIO = "num/pool";

  private final ObservableDoubleGauge resourceStateCount;

  private final ObservableDoubleGauge readyResourceRatioGauge;

  private final LongCounter handoutResourceRequestCount;

  /**
   * Gauges are read via callback. We need to keep track of the current ready resource ratio for
   * each pool id. They will be read as needed by readyResourceRatioGauge.
   */
  private final ConcurrentHashMap<String, Double> currentReadyRatioByPoolId =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<Pool, Multiset<ResourceState>> currentResourceStatesByPool =
      new ConcurrentHashMap<>();

  public MetricsHelper(OpenTelemetry openTelemetry) {
    var meter = openTelemetry.getMeter(bio.terra.common.stairway.MetricsHelper.class.getName());
    resourceStateCount =
        meter
            .gaugeBuilder(RESOURCE_STATE_COUNT_METER_NAME)
            .setDescription("Counts resource number by state")
            .setUnit(RESOURCE_TO_POOL_SIZE_RATIO)
            .buildWithCallback(
                (ObservableDoubleMeasurement m) ->
                    currentResourceStatesByPool.forEach(
                        (pool, resources) -> {
                          for (ResourceState state : ResourceState.values()) {
                            Attributes attributes =
                                Attributes.of(
                                    RESOURCE_STATE_KEY, state.toString(),
                                    POOL_ID_KEY, pool.id().id(),
                                    POOL_STATUS_KEY, pool.status().toString());
                            m.record(resources.count(state), attributes);
                          }
                        }));
    readyResourceRatioGauge =
        meter
            .gaugeBuilder(READY_RESOURCE_RATIO_METER_NAME)
            .setDescription("Ready resource count to pool size ratio")
            .setUnit(COUNT)
            .buildWithCallback(
                (ObservableDoubleMeasurement m) ->
                    currentReadyRatioByPoolId.forEach(
                        (poolId, ratio) -> m.record(ratio, Attributes.of(POOL_ID_KEY, poolId))));
    handoutResourceRequestCount =
        meter
            .counterBuilder(HANDOUT_RESOURCE_REQUEST_COUNT_METER_NAME)
            .setDescription("Handout resource request count")
            .setUnit(COUNT)
            .build();
  }

  /**
   * Records the latest count of {@link PoolAndResourceStates} and ready resource count to pool size
   * ratio.
   */
  public void recordResourceStateCount(PoolAndResourceStates poolAndResourceStates) {
    Multiset<ResourceState> resourceStates = poolAndResourceStates.resourceStates();
    this.currentResourceStatesByPool.put(poolAndResourceStates.pool(), resourceStates);
    this.currentReadyRatioByPoolId.put(
        poolAndResourceStates.pool().id().id(), getReadyResourceRatio(poolAndResourceStates));
  }

  /** Records a handout resource request event. */
  public void recordHandoutResourceRequest(PoolId poolId) {
    Attributes attributes = Attributes.of(POOL_ID_KEY, poolId.id());
    handoutResourceRequestCount.add(1, attributes);
  }

  /**
   * Gets the ready resource count to pool size ratio. For deactivated pools, the ratio would be 1.
   */
  private double getReadyResourceRatio(PoolAndResourceStates poolAndResourceStates) {
    return (poolAndResourceStates.pool().status().equals(PoolStatus.ACTIVE)
        ? poolAndResourceStates.resourceStates().count(ResourceState.READY)
            * 1.0
            / poolAndResourceStates.pool().size()
        : 1);
  }

  @Override
  public void close() throws Exception {
    readyResourceRatioGauge.close();
  }
}
