package bio.terra.rbs.service.pool;

import bio.terra.rbs.app.configuration.PoolConfiguration;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Service to handle pool operations. */
@Component
public class PoolService {
  private final Logger logger = LoggerFactory.getLogger(PoolService.class);

  private final PoolConfiguration poolConfiguration;

  public void initialize() {

    try {
      // TODO(CA-941): Determine if Stairway and Janitor database migrations need to be coordinated.
      stairway.initialize(
          stairwayJdbcConfiguration.getDataSource(),
          stairwayConfiguration.isForceCleanStart(),
          stairwayConfiguration.isMigrateUpgrade());
      // TODO(CA-941): Get obsolete Stairway instances from k8s for multi-instance stairway.
      stairway.recoverAndStart(ImmutableList.of());
    } catch (StairwayException | InterruptedException e) {
      status = Status.ERROR;
      throw new RuntimeException("Error starting Stairway", e);
    }
    status = Status.OK;
  }
}
