package bio.terra.rbs.db;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.rbs.app.configuration.RbsJdbcConfiguration;
import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@AutoConfigureMockMvc
public class RbsDaoTest extends BaseUnitTest {
  @Autowired RbsJdbcConfiguration jdbcConfiguration;
  @Autowired RbsDao rbsDao;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  public void setup() {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void createPoolAndRetrievePools() {
    Instant now = Instant.now();
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("resourceName")
            .gcpProjectConfig(new GcpProjectConfig().projectIDPrefix("test"));
    Pool pool1 =
        Pool.builder()
            .id(PoolId.create(UUID.randomUUID()))
            .creation(now)
            .name("pool1")
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(resourceConfig)
            .status(PoolStatus.ACTIVE)
            .build();
    Pool pool2 =
        Pool.builder()
            .id(PoolId.create(UUID.randomUUID()))
            .creation(now)
            .name("pool2")
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(2)
            .resourceConfig(resourceConfig)
            .status(PoolStatus.ACTIVE)
            .build();

    rbsDao.createPools(ImmutableList.of(pool1, pool2));

    List<Pool> pools = rbsDao.retrievePools(PoolStatus.ACTIVE);
    assertThat(pools, Matchers.containsInAnyOrder(pool1, pool2));
  }
}
