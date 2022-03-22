package bio.terra.buffer.service.pool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.RequestHandoutId;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.common.exception.NotFoundException;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.generated.model.PoolConfig;
import bio.terra.buffer.generated.model.PoolInfo;
import bio.terra.buffer.generated.model.ProjectIdSchema;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.generated.model.ResourceInfo;
import bio.terra.common.exception.BadRequestException;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
  public void updateFromConfig_createDeactivateReactivate_success() throws Exception {
    // Create two pools so we fully exercise the batch methods in the DAO.
    PoolId poolId1 = PoolId.create("poolId1");
    PoolWithResourceConfig parsedPoolConfig1 =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId1.toString())
                .size(10)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    PoolId poolId2 = PoolId.create("poolId2");
    PoolWithResourceConfig parsedPoolConfig2 =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId2.toString())
                .size(50)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig1, parsedPoolConfig2));
    List<Pool> pools = bufferDao.retrievePools();

    assertEquals(2, pools.size());
    Pool createdPool1 =
        pools.stream().filter(p -> p.id().equals(poolId1)).findFirst().orElseThrow();
    assertEquals(poolId1, createdPool1.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool1.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool1.status());
    assertEquals(parsedPoolConfig1.resourceConfig(), createdPool1.resourceConfig());

    Pool createdPool2 =
        pools.stream().filter(p -> p.id().equals(poolId2)).findFirst().orElseThrow();
    ;
    assertEquals(poolId2, createdPool2.id());
    assertEquals(ResourceType.GOOGLE_PROJECT, createdPool2.resourceType());
    assertEquals(PoolStatus.ACTIVE, createdPool2.status());
    assertEquals(parsedPoolConfig1.resourceConfig(), createdPool1.resourceConfig());

    // Deactivate both pools
    poolService.updateFromConfig(Collections.emptyList());
    List<Pool> deactivatedPools = bufferDao.retrievePools();
    assertEquals(2, deactivatedPools.size());
    Pool deactivatedPool1 =
        deactivatedPools.stream().filter(p -> p.id().equals(poolId1)).findFirst().orElseThrow();
    assertEquals(ResourceType.GOOGLE_PROJECT, deactivatedPool1.resourceType());
    assertEquals(PoolStatus.DEACTIVATED, deactivatedPool1.status());

    Pool deactivatedPool2 =
        deactivatedPools.stream().filter(p -> p.id().equals(poolId2)).findFirst().orElseThrow();
    assertEquals(ResourceType.GOOGLE_PROJECT, deactivatedPool2.resourceType());
    assertEquals(PoolStatus.DEACTIVATED, deactivatedPool2.status());

    // Re-activate pools
    // Resize pool 2
    PoolWithResourceConfig resizedPoolConfig2 =
        PoolWithResourceConfig.create(
            new PoolConfig()
                .poolId(poolId2.toString())
                .size(25)
                .resourceConfigName(RESOURCE_CONFIG_NAME),
            newResourceConfig());

    poolService.updateFromConfig(ImmutableList.of(parsedPoolConfig1, resizedPoolConfig2));
    List<Pool> reactivatedPools = bufferDao.retrievePools();
    Pool reactivatedPool1 =
        reactivatedPools.stream().filter(p -> p.id().equals(poolId1)).findFirst().orElseThrow();
    assertEquals(ResourceType.GOOGLE_PROJECT, reactivatedPool1.resourceType());
    assertEquals(PoolStatus.ACTIVE, reactivatedPool1.status());
    assertEquals(10, reactivatedPool1.size());

    Pool reactivatedPool2 =
        reactivatedPools.stream().filter(p -> p.id().equals(poolId2)).findFirst().orElseThrow();
    assertEquals(ResourceType.GOOGLE_PROJECT, reactivatedPool2.resourceType());
    assertEquals(PoolStatus.ACTIVE, reactivatedPool2.status());
    assertEquals(25, reactivatedPool2.size());
  }

  @Test
  public void handoutResource_success() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");
    newReadyPool(poolId, 2);
    List<CloudResourceUid> resourceUids =
        bufferDao.retrieveResourcesRandomly(poolId, ResourceState.READY, 2).stream()
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
