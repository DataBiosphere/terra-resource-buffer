package bio.terra.buffer.common.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.ResourceState;
import bio.terra.cloudres.util.MetricsHelper;
import com.google.common.collect.ImmutableList;
import io.opencensus.stats.AggregationData;
import io.opencensus.stats.View;
import io.opencensus.tags.TagValue;
import java.util.List;

/** Helper class for metrics in tests. */
public class MetricsTestUtil {
  /** Creates the resource state count view tag values for an ACTIVE pool */
  public static List<TagValue> getResourceCountTags(
      PoolId poolId, ResourceState state, PoolStatus poolStatus) {
    return ImmutableList.of(
        TagValue.create(poolId.id()),
        TagValue.create(poolStatus.toString()),
        TagValue.create(state.toString()));
  }

  /** Creates list of {@link TagValue} which only contains {@link PoolId} */
  public static List<TagValue> getPoolIdTag(PoolId poolId) {
    return ImmutableList.of(TagValue.create(poolId.id()));
  }

  /** Asserts value matches the long value with given {@link TagValue} */
  public static void assertLongValueLongIs(View.Name viewName, List<TagValue> tags, long value) {
    assertEquals(
        AggregationData.LastValueDataLong.create(value),
        MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
  }

  /** Asserts value matches the double value with given {@link TagValue} */
  public static void assertLastValueDoubleIs(
      View.Name viewName, List<TagValue> tags, double value) {
    assertEquals(
        AggregationData.LastValueDataDouble.create(value),
        MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
  }

  /**
   * Helper method to get current stats before test.
   *
   * <p>The clear stats in opencensus is not public, so we have to keep track of each stats and
   * verify the increment.
   */
  public static long getCurrentCount(View.Name viewName, List<TagValue> tags) {
    AggregationData.CountData currentCount =
        (AggregationData.CountData)
            (MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
    return currentCount == null ? 0 : currentCount.getCount();
  }

  /**
   * Assert the count is a value. 0 is equivalent to no count being present'
   *
   * <p>The clear stats in opencensus is not public, so we have to keep track of each stats and
   * verify the increment.
   */
  public static void assertCountIncremented(
      View.Name viewName, List<TagValue> tags, long previous, long increment) {
    if (previous == 0 && increment == 0) {
      assertNull(MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
    } else {
      assertEquals(
          AggregationData.CountData.create(increment + previous),
          MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
    }
  }

  /**
   * Wait for a duration longer than reporting duration (5s) to ensure spans are exported.
   *
   * <p>Values from
   * https://github.com/census-instrumentation/opencensus-java/blob/5be70440b53815eec1ab59513390aadbcec5cc9c/examples/src/main/java/io/opencensus/examples/helloworld/QuickStart.java#L106
   */
  public static void sleepForSpansExport() throws InterruptedException {
    Thread.sleep(5100);
  }
}
