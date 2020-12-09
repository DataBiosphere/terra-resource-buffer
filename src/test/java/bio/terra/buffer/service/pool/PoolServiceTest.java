package bio.terra.buffer.service.pool;

import static bio.terra.buffer.common.MetricsHelper.HANDOUT_RESOURCE_COUNT_VIEW;
import static bio.terra.buffer.common.testing.MetricsTestUtil.*;
import static bio.terra.buffer.common.testing.MetricsTestUtil.getPoolIdTag;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.buffer.common.*;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.exception.BadRequestException;
import bio.terra.buffer.common.exception.NotFoundException;
import bio.terra.buffer.db.*;
import bio.terra.buffer.generated.model.*;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.TransactionStatus;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PoolServiceTest extends BaseUnitTest {
  @Autowired PoolService poolService;
  @Autowired BufferDao bufferDao;
  TransactionStatus transactionStatus;

  private static final String RESOURCE_CONFIG_NAME = "aou_ws_resource_v1";

  private static ResourceConfig newResourceConfig(GcpProjectConfig gcpProjectConfig) {
    return new ResourceConfig().configName(RESOURCE_CONFIG_NAME).gcpProjectConfig(gcpProjectConfig);
  }

  private static ResourceConfig newResourceConfig() {
    return newResourceConfig(
        new GcpProjectConfig()
            .projectIdSchema(
                new ProjectIdSchema()
                    .prefix("aou-rw-test")
                    .scheme(ProjectIdSchema.SchemeEnum.RANDOM_CHAR))
            .enabledApis(ImmutableList.of("bigquery-json.googleapis.com")));
  }

  private static CloudResourceUid newProjectUid() {
    return new CloudResourceUid()
        .googleProjectUid(new GoogleProjectUid().projectId(UUID.randomUUID().toString()));
  }

  @Test
  public void updateFromConfig_createPool() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    List<Pool> pools = bufferDao.retrievePools();

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());
    assertEquals(parsedPoolConfig.resourceConfig(), createdPool.resourceConfig());
  }

  @Test
  public void updateFromConfig_alreadyExists() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    List<Pool> pools = bufferDao.retrievePools();

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());
    assertEquals(parsedPoolConfig.resourceConfig(), createdPool.resourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    assertThat(bufferDao.retrievePools(), Matchers.containsInAnyOrder(pools.toArray()));
  }

  @Test
  public void updateFromConfig_updateResourceConfigOnExistingPool_throwsException()
      throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolConfig poolConfig =
        new PoolConfig().poolId(poolId.toString()).size(1).resourceConfigName(RESOURCE_CONFIG_NAME);
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(poolConfig, newResourceConfig());
    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));

    // Sets ResourceConfig's GCP project id prefix to newer value.
    PoolWithResourceConfig updatedPoolConfig =
        PoolWithResourceConfig.create(
            poolConfig,
            newResourceConfig(
                new GcpProjectConfig()
                    .projectIdSchema(
                        new ProjectIdSchema()
                            .prefix("aou-rw-test2")
                            .scheme(ProjectIdSchema.SchemeEnum.RANDOM_CHAR))));

    assertThrows(
        RuntimeException.class,
        () -> poolService.updateFromConfig(ImmutableList.of(updatedPoolConfig)));
    assertThat(
        bufferDao.retrievePools().stream().map(Pool::resourceConfig).collect(Collectors.toList()),
        Matchers.containsInAnyOrder(parsedPoolConfig.resourceConfig()));
  }

  @Test
  public void updateFromConfig_deactivatedPoolExistsWithDuplicatePoolId_throwException()
      throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    bufferDao.deactivatePools(ImmutableList.of(poolId));
    List<Pool> pools = bufferDao.retrievePools();

    Pool createdPool = pools.get(0);
    assertEquals(PoolStatus.DEACTIVATED, createdPool.status());

    assertThrows(
        RuntimeException.class,
        () -> poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig)));
  }

  @Test
  public void updateFromConfig_deactivatePool_updatePoolStatusSuccess() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    List<Pool> pools = bufferDao.retrievePools();

    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(PoolStatus.ACTIVE, createdPool.status());

    poolService.updateFromConfig(ImmutableList.of());

    Pool resizedPool = bufferDao.retrievePools().get(0);
    assertEquals(poolId, resizedPool.id());
    assertEquals(PoolStatus.DEACTIVATED, resizedPool.status());
  }

  @Test
  public void updateFromConfig_updateSize_updatePoolSizeSuccess() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    int size = 10;
    PoolWithResourceConfig parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(size)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    List<Pool> pools = bufferDao.retrievePools();

    Pool createdPool = pools.get(0);
    assertEquals(poolId, createdPool.id());
    assertEquals(size, createdPool.size());

    parsedPoolConfig =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId.toString())
                .size(size + 10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig));
    assertEquals(bufferDao.retrievePools().get(0), createdPool.toBuilder().size(size + 10).build());
  }

  @Test
  public void handoutResource_success() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    long currentCount =
        getCurrentCount(HANDOUT_RESOURCE_COUNT_VIEW.getName(), getPoolIdTag(poolId));
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");
    newReadyPool(poolId, 2);
    List<CloudResourceUid> resourceUids =
        bufferDao.retrieveResources(poolId, ResourceState.READY, 2).stream()
            .map(Resource::cloudResourceUid)
            .collect(Collectors.toList());
    ResourceInfo resourceInfo = poolService.handoutResource(poolId, requestHandoutId);

    assertEquals(poolId.id(), resourceInfo.getPoolId());
    assertEquals(requestHandoutId.id(), resourceInfo.getRequestHandoutId());
    // CloudResource may be either of the two resources.
    assertTrue(
        resourceInfo.getCloudResourceUid().equals(resourceUids.get(0))
            || resourceInfo.getCloudResourceUid().equals(resourceUids.get(1)));

    // Use the same requestHandoutId, expect to get the same resource back.
    ResourceInfo secondResourceInfo = poolService.handoutResource(poolId, requestHandoutId);
    assertEquals(resourceInfo, secondResourceInfo);

    // Verify the metric record this event twice
    sleepForSpansExport();
    assertCountIncremented(
        HANDOUT_RESOURCE_COUNT_VIEW.getName(), getPoolIdTag(poolId), currentCount, 2);
  }

  @Test
  public void handoutResource_deactivatedPool() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");
    newReadyPool(poolId, 1);
    bufferDao.deactivatePools(ImmutableList.of(poolId));

    assertThrows(
        BadRequestException.class, () -> poolService.handoutResource(poolId, requestHandoutId));
  }

  @Test
  public void handoutResource_noReadyResource() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");
    newReadyPool(poolId, 0);

    assertThrows(
        NotFoundException.class, () -> poolService.handoutResource(poolId, requestHandoutId));
  }

  @Test
  public void getPoolInfo_success() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    newReadyPool(poolId, 2);

    assertEquals(
        new PoolInfo()
            .poolConfig(
                new PoolConfig()
                    .size(2)
                    .poolId(poolId.toString())
                    .resourceConfigName("resourceName"))
            .status(bio.terra.buffer.generated.model.PoolStatus.ACTIVE)
            .putResourceStateCountItem(ResourceState.READY.name(), 2)
            .putResourceStateCountItem(ResourceState.CREATING.name(), 0)
            .putResourceStateCountItem(ResourceState.DELETED.name(), 0)
            .putResourceStateCountItem(ResourceState.HANDED_OUT.name(), 0),
        poolService.getPoolInfo(poolId));
  }

  @Test
  public void getPoolInfo_notFound() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    assertThrows(NotFoundException.class, () -> poolService.getPoolInfo(poolId));
  }

  /** Creates a pool with resources with given size. */
  private void newReadyPool(PoolId poolId, int poolSize) {
    Pool pool =
        Pool.builder()
            .creation(Instant.now())
            .id(poolId)
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(poolSize)
            .resourceConfig(new ResourceConfig().configName("resourceName"))
            .status(PoolStatus.ACTIVE)
            .build();
    bufferDao.createPools(ImmutableList.of(pool));

    for (int i = 0; i < poolSize; i++) {
      ResourceId id = ResourceId.create(UUID.randomUUID());
      bufferDao.createResource(
          Resource.builder()
              .id(id)
              .poolId(poolId)
              .creation(Instant.now())
              .state(ResourceState.CREATING)
              .build());
      bufferDao.updateResourceAsReady(id, newProjectUid());
    }
  }
}
