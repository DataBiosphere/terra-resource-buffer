package bio.terra.rbs.app;

import bio.terra.rbs.app.configuration.RbsJdbcConfiguration;
import bio.terra.rbs.service.migrate.MigrateService;
import bio.terra.rbs.service.pool.PoolService;
import bio.terra.rbs.service.stairway.StairwayComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Initializes the application after the application is setup, but before the port is opened for
 * business. The purpose for this class is for database initialization and migration.
 */
public final class StartupInitializer {
  private static final Logger logger = LoggerFactory.getLogger(StartupInitializer.class);
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    MigrateService migrateService = applicationContext.getBean(MigrateService.class);
    RbsJdbcConfiguration rbsJdbcConfiguration =
        applicationContext.getBean(RbsJdbcConfiguration.class);
    PoolService poolService = applicationContext.getBean(PoolService.class);

    if (rbsJdbcConfiguration.isRecreateDbOnStart()) {
      migrateService.initialize(changelogPath, rbsJdbcConfiguration.getDataSource());
    } else if (rbsJdbcConfiguration.isUpdateDbOnStart()) {
      migrateService.upgrade(changelogPath, rbsJdbcConfiguration.getDataSource());
    }
    applicationContext.getBean(StairwayComponent.class).initialize();
    poolService.initialize();
  }
}
