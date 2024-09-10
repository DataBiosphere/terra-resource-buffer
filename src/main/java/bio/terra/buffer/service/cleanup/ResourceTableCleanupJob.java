package bio.terra.buffer.service.cleanup;

import bio.terra.buffer.app.configuration.ResourceTableCleanupJobConfiguration;
import bio.terra.buffer.db.BufferDao;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically cleans up the resource table by removing records that are either: 1) Deleted 2)
 * Handed-out, and older than the configured retention period
 */
@Component
public class ResourceTableCleanupJob {

  private final Logger logger = LoggerFactory.getLogger(ResourceTableCleanupJob.class);
  private final BufferDao bufferDao;
  private final ResourceTableCleanupJobConfiguration config;

  @Autowired
  public ResourceTableCleanupJob(BufferDao bufferDao, ResourceTableCleanupJobConfiguration config) {
    this.bufferDao = bufferDao;
    this.config = config;
  }

  /** Initialize any job related resources. For now, we are just logging configuration. */
  public void initialize() {
    logger.info("Resource table cleanup job enabled = {}", config.isEnabled());
    logger.info("Resource table cleanup job schedule = {}", config.getSchedule());
  }

  @Scheduled(cron = "${buffer.resource-table-cleanup-job.schedule}")
  @SchedulerLock(name = "ResourceTableCleanupJob", lockAtMostFor = "PT1H")
  public void cleanupResourceTable() {
    if (!config.isEnabled()) {
      logger.info(
          "Resource table cleanup job scheduled to run but job is disabled, skipping this run.");
      return;
    }

    if (config.getRetentionDays() <= 0) {
      logger.warn(
          "Resource table cleanup job scheduled to run but retention days is <= 0, skipping this run.");
      return;
    }

    if (config.getBatchSize() <= 0) {
      logger.warn(
          "Resource table cleanup job scheduled to run but batch size is <= 0, skipping this run.");
      return;
    }

    // Cleanup resource table
    logger.info("Starting resource table cleanup job");
    logger.info("Removing dead resource records older than {} days", config.getRetentionDays());
    var deletedResourceRecords =
        bufferDao.removeDeadResourceRecords(config.getRetentionDays(), config.getBatchSize());
    logger.info(
        "Resource table cleanup job removed = {} dead resource records", deletedResourceRecords);

    // cleanup the cleanup_record table
    logger.info("Removing dead cleanup records older than {} days", config.getRetentionDays());
    var deletedCleanupRecords =
        bufferDao.removeDeadCleanupRecords(config.getRetentionDays(), config.getBatchSize());
    logger.info(
        "Resource table cleanup job removed = {} dead cleanup records", deletedCleanupRecords);

    logger.info("Resource table cleanup job completed");
  }
}
