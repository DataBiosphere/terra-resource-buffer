package bio.terra.buffer.app.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * This configuration is to rate limit on project creation/deletion.
 *
 * <p>Rate limiting: The bottleneck for GCP quota comes from ServiceUsage.batchEnable API. By
 * default, it is 20/100s. Here is how we estimate the time: A Creation flight takes 600~900
 * seconds. If a scheduler runs every 10 seconds, in 900 seconds it schedulers runs 70 times. If we
 * have 4 pools, it schedules ({@code resourceCreationPerPoolLimit} * 4) = 4 flights. In total 70
 * times run, 280 flights is scheduled. We estimate this number should works when pool number is
 * 1~10. If we see more errors, we will comeback and revise those configs.
 *
 * <p>TODO(PF-617): The current 'primary' scheduling service is actually running in all instances,
 * figure out a way to make this only runs in one pod.
 */
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "buffer.primary")
public class PrimaryConfiguration {

  /** Whether to run the scheduler to periodically. */
  private boolean schedulerEnabled;

  /**
   * How often to query for flights to submit.
   *
   * <p>Sets this to be a larger number because most of the service is deployed in a multi-instance
   * world, and there will be more than one scheduling service running.
   */
  private Duration flightSubmissionPeriod = Duration.ofSeconds(30);

  /**
   * Whether to delete resource when resource count exceeds the pool size.
   *
   * <p>This to prevent multiple instances concurrent scheduler deleting extra resources. Also this
   * is no use case that we need to actively reduce pool size. Resource is only need to be deleted
   * when pool is inactivated.
   */
  private boolean deleteExcessResources = false;

  /**
   * How many resource creation flights for a pool to process simultaneously because we don't want a
   * pool that eats all flights.
   */
  private int resourceCreationPerPoolLimit = 1;

  /**
   * How many resource deletion flights for a pool to process simultaneously because we don't want a
   * pool that eats all flights.
   */
  private int resourceDeletionPerPoolLimit = 1;

  public boolean isSchedulerEnabled() {
    return schedulerEnabled;
  }

  public Duration getFlightSubmissionPeriod() {
    return flightSubmissionPeriod;
  }

  public int getResourceCreationPerPoolLimit() {
    return resourceCreationPerPoolLimit;
  }

  public int getResourceDeletionPerPoolLimit() {
    return resourceDeletionPerPoolLimit;
  }

  public void setSchedulerEnabled(boolean schedulerEnabled) {
    this.schedulerEnabled = schedulerEnabled;
  }

  public void setFlightSubmissionPeriod(Duration flightSubmissionPeriod) {
    this.flightSubmissionPeriod = flightSubmissionPeriod;
  }

  public void setResourceCreationPerPoolLimit(int resourceCreationPerPoolLimit) {
    this.resourceCreationPerPoolLimit = resourceCreationPerPoolLimit;
  }

  public void setResourceDeletionPerPoolLimit(int resourceDeletionPerPoolLimit) {
    this.resourceDeletionPerPoolLimit = resourceDeletionPerPoolLimit;
  }

  public boolean isDeleteExcessResources() {
    return deleteExcessResources;
  }

  public void setDeleteExcessResources(boolean deleteExcessResources) {
    this.deleteExcessResources = deleteExcessResources;
  }
}
