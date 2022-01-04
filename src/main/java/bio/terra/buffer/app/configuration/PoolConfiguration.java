package bio.terra.buffer.app.configuration;

import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "buffer.pool")
public class PoolConfiguration {
  // The path to have pool and resource config files.
  private String configPath;

  // Whether to update pool from pool config when server start.
  private boolean updatePoolOnStart;

  // Alternative system file path to read the config files from.
  private String systemFilePath;

  public String getConfigPath() {
    return configPath;
  }

  public void setConfigPath(String configPath) {
    this.configPath = configPath;
  }

  public boolean isUpdatePoolOnStart() {
    return updatePoolOnStart;
  }

  public void setUpdatePoolOnStart(boolean updatePoolOnStart) {
    this.updatePoolOnStart = updatePoolOnStart;
  }

  public Optional<String> getReadConfigFromSystemFile() {
    return Optional.ofNullable(systemFilePath);
  }

  public void setSystemFilePath(String systemFilePath) {
    this.systemFilePath = systemFilePath;
  }
}
