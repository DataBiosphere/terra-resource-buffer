package bio.terra.buffer.db;

import bio.terra.buffer.app.configuration.DbCleanupJobConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically cleans up the resource and cleanup_record tables by removing records that are
 * either: 1) Deleted 2) Handed-out, and older than the configured retention period
 */
@Component
public class DbCleanupJob {

  private final Logger logger = LoggerFactory.getLogger(DbCleanupJob.class);
  private final BufferDao bufferDao;
  private final DbCleanupJobConfiguration config;

  @Autowired
  public DbCleanupJob(BufferDao bufferDao, DbCleanupJobConfiguration config) {
    this.bufferDao = bufferDao;
    this.config = config;
  }

  /** Initialize any job related resources. For now, we are just logging configuration. */
  public void initialize() {
    logger.info("DB table cleanup job enabled = {}", config.isEnabled());
    logger.info("DB table cleanup job schedule = {}", config.getSchedule());
  }

  @Scheduled(cron = "${buffer.db.cleanup-job.schedule}")
  @SchedulerLock(name = "Table Cleanup Job", lockAtMostFor = "PT1H")
  public void tableCleanupJob() {
    if (!config.isEnabled()) {
      logger.info("DB table cleanup job scheduled to run but job is disabled, skipping this run.");
      return;
    }

    if (config.getRetentionDays() <= 0) {
      logger.warn(
          "DB table cleanup job scheduled to run but retention days is <= 0, skipping this run.");
      return;
    }

    if (config.getBatchSize() <= 0) {
      logger.warn(
          "DB table cleanup job scheduled to run but batch size is <= 0, skipping this run.");
      return;
    }

    // Cleanup resource table
    logger.info("Starting DB table cleanup job");
    logger.info("DB dead resource records older than {} days", config.getRetentionDays());
    var deletedResourceRecords =
        bufferDao.removeDeadResourceRecords(config.getRetentionDays(), config.getBatchSize());
    logger.info("DB table cleanup job removed = {} dead resource records", deletedResourceRecords);

    // cleanup the cleanup_record table
    logger.info("DB dead cleanup records older than {} days", config.getRetentionDays());
    var deletedCleanupRecords =
        bufferDao.removeDeadCleanupRecords(config.getRetentionDays(), config.getBatchSize());
    logger.info("DB table cleanup job removed = {} dead cleanup records", deletedCleanupRecords);

    logger.info("DB table cleanup job completed");
  }
}
