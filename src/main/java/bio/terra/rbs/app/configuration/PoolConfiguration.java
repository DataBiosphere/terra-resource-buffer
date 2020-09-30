package bio.terra.rbs.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "rbs.pool")
public class PoolConfiguration {
  // The path to have pool and resource config files.
  private String configPath;
  private boolean updateDbOnStart;

  public String getConfigPath() {
    return configPath;
  }

  public void setConfigPath(String configPath) {
    this.configPath = configPath;
  }
}
