package bio.terra.rbs.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.app.configuration.RbsJdbcConfiguration;
import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.generated.model.GcpProjectConfig;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
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
  public void retrievePoolWithResourceCount() {
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

    assertThat(
        rbsDao.retrievePoolAndResourceStates(),
        Matchers.containsInAnyOrder(
            PoolAndResourceStates.builder()
                .setPool(pool1)
                .setResourceStateCount(ResourceState.CREATING, 1)
                .setResourceStateCount(ResourceState.READY, 2)
                .build(),
            PoolAndResourceStates.builder()
                .setPool(pool2)
                .setResourceStateCount(ResourceState.READY, 1)
                .setResourceStateCount(ResourceState.HANDED_OUT, 1)
                .build(),
            PoolAndResourceStates.builder().setPool(pool3).build()));
  }

  @Test
  public void createAndRetrieveResource() {
    PoolId poolId = PoolId.create("poolId");
    Pool pool = newPool(poolId);
    rbsDao.createPools(ImmutableList.of(pool));
    Resource resource = newResource(poolId, ResourceState.CREATING);

    rbsDao.createResource(resource);
    assertEquals(resource, rbsDao.retrieveResource(resource.id()).get());
  }
}
