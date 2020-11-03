package bio.terra.rbs.common;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import io.opencensus.stats.*;
import io.opencensus.tags.*;

/** Helper class for recording metrics associated in RBS. */
public class MetricsHelper {
  private MetricsHelper() {}

  public static final String PREFIX = "terra/rbs/resource";

  @VisibleForTesting static final ViewManager VIEW_MANAGER = Stats.getViewManager();

  private static final Tagger TAGGER = Tags.getTagger();
  private static final StatsRecorder STATS_RECORDER = Stats.getStatsRecorder();

  private static final TagKey RESOURCE_STATE_KEY = TagKey.create("resource_state");
  private static final TagKey POOL_ID_KEY = TagKey.create("pool_id");
  private static final TagKey POOL_STATUS_KEY = TagKey.create("pool_status");

  /** Unit string for count. */
  private static final String COUNT = "1";
  /** Unit string for resource count to pool size ratio. */
  private static final String RESOURCE_TO_POOL_SIZE_RATIO = "num/pool";

  private static final Measure.MeasureLong RESOURCE_STATE_COUNT =
      Measure.MeasureLong.create(
          PREFIX + "/resource_state_count", "Counts resource number by state.", COUNT);

  private static final Measure.MeasureDouble READY_RESOURCE_RADIO =
      Measure.MeasureDouble.create(
          PREFIX + "/ready_resource_ratio",
          "ready resource count to pool size ratio",
          RESOURCE_TO_POOL_SIZE_RATIO);

  @VisibleForTesting
  public static final View RESOURCE_STATE_COUNT_VIEW =
      View.create(
          View.Name.create(PREFIX + "/resource_state_count"),
          "Counts resource number by state",
          RESOURCE_STATE_COUNT,
          Aggregation.LastValue.create(),
          ImmutableList.of(RESOURCE_STATE_KEY, POOL_ID_KEY, POOL_STATUS_KEY));

  @VisibleForTesting
  public static final View READY_RESOURCE_RATIO_VIEW =
      View.create(
          View.Name.create(PREFIX + "/ready_resource_ratio"),
          "The ratio of ready resource count to pool size ratio, in #.## format",
          READY_RESOURCE_RADIO,
          Aggregation.LastValue.create(),
          ImmutableList.of(POOL_ID_KEY));

  private static final ImmutableList<View> VIEWS =
      ImmutableList.of(RESOURCE_STATE_COUNT_VIEW, READY_RESOURCE_RATIO_VIEW);

  // Register all views
  static {
    for (View view : VIEWS) {
      VIEW_MANAGER.registerView(view);
    }
  }

  /**
   * Records the latest count of {@link PoolAndResourceStates} and ready resource count to pool size
   * ratio for ACTIVATE pools.
   */
  public static void recordResourceStateCount(PoolAndResourceStates poolAndResourceStates) {
    Multiset<ResourceState> resourceStates = poolAndResourceStates.resourceStates();
    resourceStates.entrySet();
    for (Multiset.Entry<ResourceState> resourceStateEntry : resourceStates.entrySet()) {
      TagContext tctx =
          TAGGER
              .emptyBuilder()
              .putLocal(
                  RESOURCE_STATE_KEY, TagValue.create(resourceStateEntry.getElement().toString()))
              .putLocal(POOL_ID_KEY, TagValue.create(poolAndResourceStates.pool().id().id()))
              .putLocal(
                  POOL_STATUS_KEY,
                  TagValue.create(poolAndResourceStates.pool().status().toString()))
              .build();

      STATS_RECORDER
          .newMeasureMap()
          .put(RESOURCE_STATE_COUNT, resourceStateEntry.getCount())
          .record(tctx);
    }

    if (poolAndResourceStates.pool().status().equals(PoolStatus.ACTIVE)) {
      TagContext tctx =
          TAGGER
              .emptyBuilder()
              .putLocal(POOL_ID_KEY, TagValue.create(poolAndResourceStates.pool().id().id()))
              .build();
      STATS_RECORDER
          .newMeasureMap()
          .put(
              READY_RESOURCE_RADIO,
              (double)
                      Math.round(
                          resourceStates.count(ResourceState.READY)
                              * 1.0
                              / poolAndResourceStates.pool().size()
                              * 100)
                  / 100)
          .record(tctx);
    }
  }
}
