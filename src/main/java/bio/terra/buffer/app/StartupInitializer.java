package bio.terra.buffer.app;

import bio.terra.buffer.app.configuration.BufferDatabaseDatabaseConfiguration;
import bio.terra.buffer.app.configuration.BufferDatabaseProperties;
import bio.terra.buffer.service.cleanup.CleanupScheduler;
import bio.terra.buffer.service.pool.PoolService;
import bio.terra.buffer.service.resource.FlightScheduler;
import bio.terra.buffer.service.stackdriver.StackdriverExporter;
import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.TracingHook;
import com.google.common.collect.ImmutableList;
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
    logger.info("Initializing the application after the application is setup");
    applicationContext.getBean(StackdriverExporter.class).initialize();
    // Initialize or upgrade the database depending on the configuration
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    BufferDatabaseDatabaseConfiguration bufferDatabaseConfiguration =
        applicationContext.getBean(BufferDatabaseDatabaseConfiguration.class);
    BufferDatabaseProperties bufferDatabaseProperties =
        applicationContext.getBean(BufferDatabaseProperties.class);

    // TODO(PF-595): This seems to be a common pattern, and we should let migrateService takes care
    // of this if-else block.
    if (bufferDatabaseProperties.isRecreateDbOnStart()) {
      migrateService.initialize(changelogPath, bufferDatabaseConfiguration.getDataSource());
    } else if (bufferDatabaseProperties.isUpdateDbOnStart()) {
      migrateService.upgrade(changelogPath, bufferDatabaseConfiguration.getDataSource());
    }
    applicationContext
        .getBean(StairwayComponent.class)
        .initialize(applicationContext, ImmutableList.of(new TracingHook()));
    applicationContext.getBean(PoolService.class).initialize();
    applicationContext.getBean(FlightScheduler.class).initialize();
    applicationContext.getBean(CleanupScheduler.class).initialize();
  }
}
