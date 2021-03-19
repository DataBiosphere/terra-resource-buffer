package bio.terra.buffer.app;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_DB_DATA_SOURCE;

import bio.terra.buffer.app.configuration.BufferDatabaseProperties;
import bio.terra.buffer.service.cleanup.CleanupScheduler;
import bio.terra.buffer.service.pool.PoolService;
import bio.terra.buffer.service.resource.FlightScheduler;
import bio.terra.buffer.service.stackdriver.StackdriverExporter;
import bio.terra.buffer.service.stairway.StairwayComponent;
import bio.terra.common.migrate.LiquibaseMigrator;
import javax.sql.DataSource;
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
    applicationContext.getBean(StackdriverExporter.class).initialize();
    // Initialize or upgrade the database depending on the configuration
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    BufferDatabaseProperties bufferDatabaseProperties =
        applicationContext.getBean(BufferDatabaseProperties.class);
    DataSource bufferDbDataSource =
        applicationContext.getBeansOfType(DataSource.class).get(BUFFER_DB_DATA_SOURCE);

    logger.warn("~~~~~~~~~initialize bufferDbDataSource.toString()");
    logger.warn("~~~~~~~~~initialize bufferDbDataSource.toString()");
    System.out.println("!!!!!!!!!!~~~~~~~");
    System.out.println("!!!!!!!!!!~~~~~~~");
    System.out.println("!!!!!!!!!!~~~~~~~");
    System.out.println("!!!!!!!!!!~~~~~~~");
    System.out.println(bufferDbDataSource);
    System.out.println(bufferDbDataSource.toString());

    if (bufferDatabaseProperties.isRecreateDbOnStart()) {
      migrateService.initialize(changelogPath, bufferDbDataSource);
    } else if (bufferDatabaseProperties.isUpdateDbOnStart()) {
      migrateService.upgrade(changelogPath, bufferDbDataSource);
    }
    applicationContext.getBean(StairwayComponent.class).initialize();
    applicationContext.getBean(PoolService.class).initialize();
    applicationContext.getBean(FlightScheduler.class).initialize();
    applicationContext.getBean(CleanupScheduler.class).initialize();
  }
}
