package bio.terra.buffer.app.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.stairway")
public class StairwayConfiguration {
  /**
   * Using a fixed Stairway name helps us keep the same record of Stairway instance. Otherwise,
   * Stairway creates random name after re-deploy, hence we can not recover previous PENDING flight.
   */
  private String name = "buffer-stairway";

  private String clusterName;
  private boolean forceCleanStart;
  private boolean migrateUpgrade;
  private int maxParallelFlights;
  private Duration quietDownTimeout;
  private Duration terminateTimeout;

  public String getName() {
    return name;
  }

  public String getClusterName() {
    return clusterName;
  }

  public boolean isForceCleanStart() {
    return forceCleanStart;
  }

  public boolean isMigrateUpgrade() {
    return migrateUpgrade;
  }

  public int getMaxParallelFlights() {
    return maxParallelFlights;
  }

  public Duration getQuietDownTimeout() {
    return quietDownTimeout;
  }

  public Duration getTerminateTimeout() {
    return terminateTimeout;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public void setForceCleanStart(boolean forceCleanStart) {
    this.forceCleanStart = forceCleanStart;
  }

  public void setMigrateUpgrade(boolean migrateUpgrade) {
    this.migrateUpgrade = migrateUpgrade;
  }

  public void setMaxParallelFlights(int maxParallelFlights) {
    this.maxParallelFlights = maxParallelFlights;
  }

  public void setQuietDownTimeout(Duration quietDownTimeout) {
    this.quietDownTimeout = quietDownTimeout;
  }

  public void setTerminateTimeout(Duration terminateTimeout) {
    this.terminateTimeout = terminateTimeout;
  }
}
