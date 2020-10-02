package bio.terra.rbs.service.pool;
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
public class PoolServiceTest {
    @Autowired PoolService poolService;

    @Test
    public void initialize() {

    }
}
