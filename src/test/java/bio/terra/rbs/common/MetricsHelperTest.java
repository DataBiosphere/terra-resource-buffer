package bio.terra.rbs.common;

import static bio.terra.rbs.common.MetricsHelper.READY_RESOURCE_RATIO_VIEW;
import static bio.terra.rbs.common.MetricsHelper.RESOURCE_STATE_COUNT_VIEW;
import static bio.terra.rbs.common.testing.MetricsTestUtil.*;

import bio.terra.rbs.generated.model.ResourceConfig;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Test for {@link bio.terra.cloudres.util.MetricsHelper} */
@Tag("unit")
public class MetricsHelperTest extends BaseUnitTest {
  private static final PoolId POOL_ID = PoolId.create("poolId");

  @Test
  public void testRecordResourceState() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolAndResourceStates resourceStates =
        PoolAndResourceStates.builder()
            .setPool(
                Pool.builder()
                    .id(poolId)
                    .size(10)
                    .creation(Instant.now())
                    .status(PoolStatus.ACTIVE)
                    .resourceType(ResourceType.GOOGLE_PROJECT)
                    .resourceConfig(new ResourceConfig().configName("configName"))
                    .build())
            .setResourceStateCount(ResourceState.READY, 2)
            .setResourceStateCount(ResourceState.HANDED_OUT, 1)
            .build();

    MetricsHelper.recordResourceStateCount(resourceStates);
    sleepForSpansExport();

    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(), getResourceCountTags(poolId, ResourceState.READY), 2);
    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(poolId, ResourceState.HANDED_OUT),
        1);
    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(poolId, ResourceState.DELETED),
        0);
    // 2 ready of size 10
    assertLastValueDoubleIs(
        READY_RESOURCE_RATIO_VIEW.getName(), getReadyResourceRatioTags(poolId), 0.20);
  }
}
