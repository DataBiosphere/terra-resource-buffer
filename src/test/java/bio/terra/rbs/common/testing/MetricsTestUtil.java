package bio.terra.rbs.common.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.cloudres.util.MetricsHelper;
import bio.terra.rbs.common.PoolId;
import bio.terra.rbs.common.PoolStatus;
import bio.terra.rbs.common.ResourceState;
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

  /** Creates the ready resource to pool size ratio count view tag values for an ACTIVE pool */
  public static List<TagValue> getReadyResourceRatioTags(PoolId poolId) {
    return ImmutableList.of(TagValue.create(poolId.id()));
  }

  /** Asserts value matches the long value with given {@link TagValue} */
  public static void assertLongValueLongIs(View.Name viewName, List<TagValue> tags, long value) {
    if (value == 0) {
      assertNull(MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
    } else {
      assertEquals(
          AggregationData.LastValueDataLong.create(value),
          MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
    }
  }

  /** Asserts value matches the double value with given {@link TagValue} */
  public static void assertLastValueDoubleIs(
      View.Name viewName, List<TagValue> tags, double value) {
    if (value == 0) {
      assertNull(MetricsHelper.viewManager.getView(viewName).getAggregationMap().get(tags));
    } else {
      assertEquals(
          AggregationData.LastValueDataDouble.create(value),
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
