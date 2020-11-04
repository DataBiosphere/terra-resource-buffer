package bio.terra.rbs.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.rbs.app.configuration.RbsJdbcConfiguration;
import bio.terra.rbs.common.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.GoogleProjectUid;
import bio.terra.rbs.generated.model.ResourceConfig;
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
public class RbsDaoTest extends BaseUnitTest {
  @Autowired RbsJdbcConfiguration jdbcConfiguration;
  @Autowired RbsDao rbsDao;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  private static Pool newPool(PoolId poolId) {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("resourceName")
            .gcpProjectConfig(new GcpProjectConfig().projectIDPrefix("test"));

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

    rbsDao.createPools(ImmutableList.of(pool1, pool2));

    List<Pool> pools = rbsDao.retrievePools();
    assertThat(pools, Matchers.containsInAnyOrder(pool1, pool2));
    assertEquals(pool1, rbsDao.retrievePool(pool1.id()).get());
    assertEquals(pool2, rbsDao.retrievePool(pool2.id()).get());
  }

  @Test
  public void deactivatePool() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);

    rbsDao.createPools(ImmutableList.of(pool));
    Pool retrievedPool = rbsDao.retrievePools().get(0);
    assertEquals(poolId, retrievedPool.id());
    assertEquals(PoolStatus.ACTIVE, retrievedPool.status());

    rbsDao.deactivatePools(ImmutableList.of(poolId));
    retrievedPool = rbsDao.retrievePools().get(0);
    assertEquals(poolId, retrievedPool.id());
    assertEquals(PoolStatus.DEACTIVATED, retrievedPool.status());
  }

  @Test
  public void updatePoolSize() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);

    rbsDao.createPools(ImmutableList.of(pool));
    Pool retrievedPool = rbsDao.retrievePools().get(0);
    Pool resizedPool = pool.toBuilder().size(retrievedPool.size() + 10).build();

    rbsDao.updatePoolsSize(ImmutableMap.of(poolId, resizedPool.size()));
    assertThat(rbsDao.retrievePools(), Matchers.containsInAnyOrder(resizedPool));
  }

  @Test
  public void retrievePoolWithResourceState() {
    Pool pool1 = newPool(PoolId.create("poolId1"));
    Pool pool2 = newPool(PoolId.create("poolId2"));
    Pool pool3 = newPool(PoolId.create("poolId3"));

    // Pool1 has 1 CREATING, 2 READY, Pool2 has 1 READY, 1 HANDED_OUT, Pool3 is empty
    rbsDao.createPools(ImmutableList.of(pool1, pool2, pool3));
    rbsDao.createResource(newResource(pool1.id(), ResourceState.CREATING));
    rbsDao.createResource(newResource(pool1.id(), ResourceState.READY));
    rbsDao.createResource(newResource(pool1.id(), ResourceState.READY));
    rbsDao.createResource(newResource(pool2.id(), ResourceState.READY));
    rbsDao.createResource(newResource(pool2.id(), ResourceState.HANDED_OUT));

    PoolAndResourceStates pool1State =
        PoolAndResourceStates.builder()
            .setPool(pool1)
            .setResourceStateCount(ResourceState.CREATING, 1)
            .setResourceStateCount(ResourceState.READY, 2)
            .build();

    assertThat(
        rbsDao.retrievePoolAndResourceStates(),
        Matchers.containsInAnyOrder(
            pool1State,
            PoolAndResourceStates.builder()
                .setPool(pool2)
                .setResourceStateCount(ResourceState.READY, 1)
                .setResourceStateCount(ResourceState.HANDED_OUT, 1)
                .build(),
            PoolAndResourceStates.builder().setPool(pool3).build()));

    assertEquals(pool1State, rbsDao.retrievePoolAndResourceStatesById(pool1.id()).get());
  }

  @Test
  public void createRetrieveDeleteResource() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);
    rbsDao.createPools(ImmutableList.of(pool));
    Resource resource = newResource(poolId, ResourceState.CREATING);

    rbsDao.createResource(resource);
    assertEquals(resource, rbsDao.retrieveResource(resource.id()).get());

    assertTrue(rbsDao.deleteResource(resource.id()));
    assertFalse(rbsDao.retrieveResource(resource.id()).isPresent());
  }

  @Test
  public void updateResourceAsReady() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);
    rbsDao.createPools(ImmutableList.of(pool));
    Resource resource = newResource(poolId, ResourceState.CREATING);
    CloudResourceUid resourceUid =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p-123"));
    rbsDao.createResource(resource);

    rbsDao.updateResourceAsReady(resource.id(), resourceUid);
    Resource updatedResource = rbsDao.retrieveResource(resource.id()).get();
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
    rbsDao.createPools(ImmutableList.of(pool));
    rbsDao.createResource(creating);
    rbsDao.createResource(ready1);
    rbsDao.createResource(ready2);
    rbsDao.createResource(ready3);

    List<Resource> resources = rbsDao.retrieveResources(pool.id(), ResourceState.READY, 2);
    assertEquals(2, resources.size());
    assertThat(ImmutableList.of(ready1, ready2, ready3), Matchers.hasItems(resources.toArray()));
  }

  @Test
  public void updateResourceAsHandedOut() {
    Pool pool = newPool(PoolId.create("poolId"));
    RequestHandoutId requestHandoutId = RequestHandoutId.create("handoutId");

    Resource ready = newResource(pool.id(), ResourceState.READY);
    rbsDao.createPools(ImmutableList.of(pool));
    rbsDao.createResource(ready);
    rbsDao.updateResourceAsHandedOut(ready.id(), requestHandoutId);

    List<Resource> resources = rbsDao.retrieveResources(pool.id(), ResourceState.HANDED_OUT, 1);
    assertEquals(1, resources.size());
    assertEquals(requestHandoutId, resources.get(0).requestHandoutId());
  }

  @Test
  public void updateResourceAsDeleting() {
    Pool pool = newPool(PoolId.create("poolId"));
    Resource resource = newResource(pool.id(), ResourceState.READY);
    rbsDao.createPools(ImmutableList.of(pool));
    rbsDao.createResource(resource);

    rbsDao.updateResourceAsDeleting(resource.id());
    assertEquals(ResourceState.DELETING, rbsDao.retrieveResource(resource.id()).get().state());
  }

  @Test
  public void updateResourceAsDeleted() {
    Pool pool = newPool(PoolId.create("poolId"));
    Resource resource = newResource(pool.id(), ResourceState.DELETING);
    rbsDao.createPools(ImmutableList.of(pool));
    rbsDao.createResource(resource);

    Instant now = Instant.now();
    rbsDao.updateResourceAsDeleted(resource.id(), now);
    resource = rbsDao.retrieveResource(resource.id()).get();
    assertEquals(now, resource.deletion());
    assertEquals(ResourceState.DELETED, resource.state());
  }
}
