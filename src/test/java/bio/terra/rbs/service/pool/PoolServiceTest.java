package bio.terra.rbs.service.pool;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.db.Pool;
import bio.terra.rbs.db.PoolStatus;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

@AutoConfigureMockMvc
public class PoolServiceTest extends BaseUnitTest {
  @Autowired PoolService poolService;

  @Autowired RbsDao rbsDao;

  @Test
  public void initialize() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    poolService.initializeFromConfig();
    List<Pool> pools = rbsDao.retrievePools(PoolStatus.ACTIVE);

    assertEquals(1, pools.size());
    Pool createdPool = pools.get(0);
    ResourceConfig createdResourceConfig = new ResourceConfig().gcpProjectConfig(
            new GcpProjectConfig().projectIDPrefix(aou-rw-test))
    assertEquals("aou_ws_test_v1", pools.get(0).name());
    assertEquals("aou_ws_test_v1", pools.get(0).getS());

    System.out.println("!!!!!!!");
    System.out.println(pools.get(0));
  }
}
