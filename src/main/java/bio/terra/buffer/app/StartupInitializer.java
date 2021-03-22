package bio.terra.buffer.app;

import bio.terra.buffer.app.configuration.BufferJdbcConfiguration;
import bio.terra.buffer.service.cleanup.CleanupScheduler;
import bio.terra.buffer.service.pool.PoolService;
import bio.terra.buffer.service.resource.FlightScheduler;
import bio.terra.buffer.service.stackdriver.StackdriverExporter;
import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.common.stairway.StairwayLifecycleManager;
import bio.terra.common.stairway.TracingHook;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

/**
 * Initializes the application after the application is setup, but before the port is opened for
 * business. The purpose for this class is for database initialization and migration.
 */
public final class StartupInitializer {
  private static final Logger logger = LoggerFactory.getLogger(StartupInitializer.class);
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    applicationContext.getBean(StackdriverExporter.class).initialize();
    // Initialize or upgrade the database depending on the configuration
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    BufferJdbcConfiguration bufferJdbcConfiguration =
        applicationContext.getBean(BufferJdbcConfiguration.class);
    if (bufferJdbcConfiguration.isRecreateDbOnStart()) {
      migrateService.initialize(changelogPath, bufferJdbcConfiguration.getDataSource());
    } else if (bufferJdbcConfiguration.isUpdateDbOnStart()) {
      migrateService.upgrade(changelogPath, bufferJdbcConfiguration.getDataSource());
    }
    applicationContext
        .getBean(StairwayLifecycleManager.class)
        .initialize(applicationContext, ImmutableList.of(new TracingHook()));
    applicationContext.getBean(PoolService.class).initialize();
    applicationContext.getBean(FlightScheduler.class).initialize();
    applicationContext.getBean(CleanupScheduler.class).initialize();
  }
}
