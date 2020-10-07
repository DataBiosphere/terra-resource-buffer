package bio.terra.rbs.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.app.configuration.RbsJdbcConfiguration;
import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
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
    // TODO(yonghao): Insert some resource and verify count matches once RbsDao supports insert
    // resource
    Pool pool1 = newPool(PoolId.create("poolId1"));
    Pool pool2 = newPool(PoolId.create("poolId2"));
    rbsDao.createPools(ImmutableList.of(pool1, pool2));

    assertThat(
        rbsDao.retrievePoolAndResourceStatesCount(),
        Matchers.containsInAnyOrder(
            PoolAndResourceStates.create(pool1, ImmutableMultiset.of()),
            PoolAndResourceStates.create(pool2, ImmutableMultiset.of())));
  }
}
