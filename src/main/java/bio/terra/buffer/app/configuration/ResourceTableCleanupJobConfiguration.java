package bio.terra.buffer.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "buffer.resource-table-cleanup-job")
public class ResourceTableCleanupJobConfiguration {

  private String schedule;
  private boolean enabled;
  private int retentionDays;
  private int batchSize;

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }
}
