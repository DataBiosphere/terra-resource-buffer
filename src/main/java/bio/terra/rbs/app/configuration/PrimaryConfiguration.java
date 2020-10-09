package bio.terra.rbs.app.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "rbs.primary")
public class PrimaryConfiguration {
  /** Whether to run the scheduler to periodically. */
  private boolean schedulerEnabled;

  /** How often to query for flights to submit. */
  private Duration flightSubmissionPeriod = Duration.ofMinutes(1);

  /**
   * How many resource creation flights for a pool to process simultaneously because because we
   * don't want a pool eats all flights.
   */
  private int resourceCreationPerPoolLimit = 100;

  /**
   * How many resource deletion flights for a pool to process simultaneously because we don't want a
   * pool eats all flights.
   */
  private int resourceDeletionPerPoolLimit = 50;

  /** How often to record the counts of the different resources in the database. */
  private Duration recordResourceCountPeriod = Duration.ofMinutes(10);

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
}
