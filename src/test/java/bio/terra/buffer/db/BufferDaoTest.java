package bio.terra.buffer.db;

import static bio.terra.buffer.common.ResourceState.HANDED_OUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.buffer.app.configuration.BufferJdbcConfiguration;
import bio.terra.buffer.common.*;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.generated.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class BufferDaoTest extends BaseUnitTest {
  @Autowired BufferJdbcConfiguration jdbcConfiguration;
  @Autowired BufferDao bufferDao;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  private static Pool newPool(PoolId poolId) {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("resourceName")
            .gcpProjectConfig(
                new GcpProjectConfig()
                    .projectIdSchema(
                        new ProjectIdSchema()
                            .prefix("test")
                            .scheme(ProjectIdSchema.SchemeEnum.RANDOM_CHAR)));

    return Pool.builder()
        .creation(Instant.now())
        .id(poolId)
        .resourceType(ResourceType.GOOGLE_PROJECT)
        .size(1)
        .resourceConfig(resourceConfig)
        .status(PoolStatus.ACTIVE)
        .build();
  }

  private static Resource newResource(PoolId poolId, ResourceState state) {
    return Resource.builder()
        .id(ResourceId.create(UUID.randomUUID()))
        .poolId(poolId)
        .creation(Instant.now())
        .state(state)
        .build();
  }

  @Test
  public void createPoolAndRetrievePools() {
    Pool pool1 = newPool(PoolId.create("pool1"));
    Pool pool2 = newPool(PoolId.create("pool2"));

    bufferDao.createPools(ImmutableList.of(pool1, pool2));

    List<Pool> pools = bufferDao.retrievePools();
    assertThat(pools, Matchers.containsInAnyOrder(pool1, pool2));
    assertEquals(pool1, bufferDao.retrievePool(pool1.id()).get());
    assertEquals(pool2, bufferDao.retrievePool(pool2.id()).get());
  }

  @Test
  public void deactivatePool() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);

    bufferDao.createPools(ImmutableList.of(pool));
    Pool retrievedPool = bufferDao.retrievePools().get(0);
    assertEquals(poolId, retrievedPool.id());
    assertEquals(PoolStatus.ACTIVE, retrievedPool.status());

    bufferDao.deactivatePools(ImmutableList.of(poolId));
    retrievedPool = bufferDao.retrievePools().get(0);
    assertEquals(poolId, retrievedPool.id());
    assertEquals(PoolStatus.DEACTIVATED, retrievedPool.status());
  }

  @Test
  public void updatePoolSize() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);

    bufferDao.createPools(ImmutableList.of(pool));
    Pool retrievedPool = bufferDao.retrievePools().get(0);
    Pool resizedPool = pool.toBuilder().size(retrievedPool.size() + 10).build();

    bufferDao.updatePoolsSize(ImmutableMap.of(poolId, resizedPool.size()));
    assertThat(bufferDao.retrievePools(), Matchers.containsInAnyOrder(resizedPool));
  }

  @Test
  public void retrievePoolWithResourceState() {
    Pool pool1 = newPool(PoolId.create("poolId1"));
    Pool pool2 = newPool(PoolId.create("poolId2"));
    Pool pool3 = newPool(PoolId.create("poolId3"));

    // Pool1 has 1 CREATING, 2 READY, Pool2 has 1 READY, 1 HANDED_OUT, Pool3 is empty
    bufferDao.createPools(ImmutableList.of(pool1, pool2, pool3));
    bufferDao.createResource(newResource(pool1.id(), ResourceState.CREATING));
    bufferDao.createResource(newResource(pool1.id(), ResourceState.READY));
    bufferDao.createResource(newResource(pool1.id(), ResourceState.READY));
    bufferDao.createResource(newResource(pool2.id(), ResourceState.READY));
    bufferDao.createResource(newResource(pool2.id(), HANDED_OUT));

    PoolAndResourceStates pool1State =
        PoolAndResourceStates.builder()
            .setPool(pool1)
            .setResourceStateCount(ResourceState.CREATING, 1)
            .setResourceStateCount(ResourceState.READY, 2)
            .build();

    assertThat(
        bufferDao.retrievePoolAndResourceStates(),
        Matchers.containsInAnyOrder(
            pool1State,
            PoolAndResourceStates.builder()
                .setPool(pool2)
                .setResourceStateCount(ResourceState.READY, 1)
                .setResourceStateCount(HANDED_OUT, 1)
                .build(),
            PoolAndResourceStates.builder().setPool(pool3).build()));

    assertEquals(pool1State, bufferDao.retrievePoolAndResourceStatesById(pool1.id()).get());
  }

  @Test
  public void createRetrieveDeleteResource() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);
    bufferDao.createPools(ImmutableList.of(pool));
    Resource resource = newResource(poolId, ResourceState.CREATING);

    bufferDao.createResource(resource);
    assertEquals(resource, bufferDao.retrieveResource(resource.id()).get());

    assertTrue(bufferDao.deleteResource(resource.id()));
    assertFalse(bufferDao.retrieveResource(resource.id()).isPresent());
  }

  @Test
  public void updateResourceAsReady() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);
    bufferDao.createPools(ImmutableList.of(pool));
    Resource resource = newResource(poolId, ResourceState.CREATING);
    CloudResourceUid resourceUid =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p-123"));
    bufferDao.createResource(resource);

    bufferDao.updateResourceAsReady(resource.id(), resourceUid);
    Resource updatedResource = bufferDao.retrieveResource(resource.id()).get();
    assertEquals(ResourceState.READY, updatedResource.state());
    assertEquals(resourceUid, updatedResource.cloudResourceUid());
  }

  @Test
  public void retrieveResourcesWithState() {
    Pool pool = newPool(PoolId.create("poolId1"));

    // 1 CREATING, 3 READY resources; and ask for 2 READY resources.
    Resource creating = newResource(pool.id(), ResourceState.CREATING);
    Resource ready1 = newResource(pool.id(), ResourceState.READY);
    Resource ready2 = newResource(pool.id(), ResourceState.READY);
    Resource ready3 = newResource(pool.id(), ResourceState.READY);
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(creating);
    bufferDao.createResource(ready1);
    bufferDao.createResource(ready2);
    bufferDao.createResource(ready3);

    List<Resource> resources =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, 2);
    assertEquals(2, resources.size());
    assertThat(ImmutableList.of(ready1, ready2, ready3), Matchers.hasItems(resources.toArray()));
  }

  @Test
  public void updateOneReadyResourceToHandedOut() {
    Pool pool = newPool(PoolId.create("poolId"));
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");

    Resource ready = newResource(pool.id(), ResourceState.READY);
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(ready);
    Resource resource =
        bufferDao.updateOneReadyResourceToHandedOut(pool.id(), requestHandoutId).get();

    Resource handedOutResource = bufferDao.retrieveResource(resource.id()).get();
    assertEquals(requestHandoutId, handedOutResource.requestHandoutId());
    assertEquals(HANDED_OUT, handedOutResource.state());

    // Now use the same requestHandoutId again, expect getting the same resource back.
    assertEquals(
        handedOutResource,
        bufferDao.updateOneReadyResourceToHandedOut(pool.id(), requestHandoutId).get());
  }

  @Test
  public void updateOneReadyResourceToHandedOut_noResourceAvailable() {
    Pool pool = newPool(PoolId.create("poolId"));
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");

    bufferDao.createPools(ImmutableList.of(pool));
    assertFalse(
        bufferDao.updateOneReadyResourceToHandedOut(pool.id(), requestHandoutId).isPresent());
  }

  @Test
  public void updateReadyResourceAsDeleting_success() {
    Pool pool = newPool(PoolId.create("poolId"));
    Resource resource = newResource(pool.id(), ResourceState.READY);
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(resource);

    assertTrue(bufferDao.updateReadyResourceToDeleting(resource.id()));
    assertEquals(ResourceState.DELETING, bufferDao.retrieveResource(resource.id()).get().state());
  }

  @Test
  public void updateReadyResourceAsDeleting_currentStateIsNotReady() {
    Pool pool = newPool(PoolId.create("poolId"));
    Resource resource = newResource(pool.id(), HANDED_OUT);
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(resource);

    assertFalse(bufferDao.updateReadyResourceToDeleting(resource.id()));
    assertEquals(HANDED_OUT, bufferDao.retrieveResource(resource.id()).get().state());
  }

  @Test
  public void updateResourceAsDeleted() {
    Pool pool = newPool(PoolId.create("poolId"));
    Resource resource = newResource(pool.id(), ResourceState.DELETING);
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(resource);

    Instant now = Instant.now();
    bufferDao.updateResourceAsDeleted(resource.id(), now);
    resource = bufferDao.retrieveResource(resource.id()).get();
    assertEquals(now, resource.deletion());
    assertEquals(ResourceState.DELETED, resource.state());
  }

  @Test
  public void insertAndRetrieveCleanupRecord() {
    // Prepare 2 HANDED_OUT and 1 READY resources.
    Pool pool = newPool(PoolId.create("poolId"));
    Resource handedOutR1 = newResource(pool.id(), ResourceState.READY);
    Resource handedOutR2 = newResource(pool.id(), ResourceState.READY);
    Resource readyR1 = newResource(pool.id(), ResourceState.READY);
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(handedOutR1);
    bufferDao.createResource(handedOutR2);
    bufferDao.createResource(readyR1);
    bufferDao.updateOneReadyResourceToHandedOut(pool.id(), RequestHandoutId.create("1111"));
    bufferDao.updateOneReadyResourceToHandedOut(pool.id(), RequestHandoutId.create("2222"));

    // handedOutR1 is already in cleanup_record table, expect only handedOutR2 is returned.
    bufferDao.insertCleanupRecord(handedOutR1.id());
    assertThat(
        bufferDao.retrieveResourceToCleanup(1),
        Matchers.contains(bufferDao.retrieveResource(handedOutR2.id()).get()));
  }
}
