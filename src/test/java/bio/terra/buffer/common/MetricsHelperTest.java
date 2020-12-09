package bio.terra.buffer.common;

import static bio.terra.buffer.common.MetricsHelper.*;
import static bio.terra.buffer.common.testing.MetricsTestUtil.*;

import bio.terra.buffer.generated.model.ResourceConfig;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Test for {@link bio.terra.cloudres.util.MetricsHelper} */
@Tag("unit")
public class MetricsHelperTest extends BaseUnitTest {
  @Test
  public void testRecordResourceState() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    Pool pool =
        Pool.builder()
            .id(poolId)
            .size(10)
            .creation(Instant.now())
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

    MetricsHelper.recordResourceStateCount(resourceStates);
    sleepForSpansExport();

    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(poolId, ResourceState.READY, PoolStatus.ACTIVE),
        2);
    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(poolId, ResourceState.HANDED_OUT, PoolStatus.ACTIVE),
        1);
    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(poolId, ResourceState.DELETED, PoolStatus.ACTIVE),
        0);
    // 2 ready out of size 10
    assertLastValueDoubleIs(READY_RESOURCE_RATIO_VIEW.getName(), getPoolIdTag(poolId), 0.20);

    // Now decrease READY resource count to 0, verifies it can sill count.
    PoolAndResourceStates noReadyResourceStates =
        PoolAndResourceStates.builder()
            .setPool(pool)
            .setResourceStateCount(ResourceState.HANDED_OUT, 1)
            .build();
    MetricsHelper.recordResourceStateCount(noReadyResourceStates);
    sleepForSpansExport();
    assertLongValueLongIs(
        RESOURCE_STATE_COUNT_VIEW.getName(),
        getResourceCountTags(poolId, ResourceState.READY, PoolStatus.ACTIVE),
        0);
    assertLastValueDoubleIs(READY_RESOURCE_RATIO_VIEW.getName(), getPoolIdTag(poolId), 0.0);

    // Now deactivate the pool and verifies READY resource ratio changed to 1.
    PoolAndResourceStates deactivatedResourceStates =
        PoolAndResourceStates.builder()
            .setPool(pool.toBuilder().status(PoolStatus.DEACTIVATED).build())
            .setResourceStateCount(ResourceState.HANDED_OUT, 1)
            .build();
    MetricsHelper.recordResourceStateCount(deactivatedResourceStates);
    sleepForSpansExport();
    assertLastValueDoubleIs(READY_RESOURCE_RATIO_VIEW.getName(), getPoolIdTag(poolId), 1.0);
  }

  @Test
  public void testRecordHandoutResource() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    long currentCount =
        getCurrentCount(HANDOUT_RESOURCE_COUNT_VIEW.getName(), getPoolIdTag(poolId));
    MetricsHelper.recordHandoutResource(poolId);
    MetricsHelper.recordHandoutResource(poolId);
    sleepForSpansExport();

    assertCountIncremented(
        HANDOUT_RESOURCE_COUNT_VIEW.getName(), getPoolIdTag(poolId), currentCount, 2);
  }
}
