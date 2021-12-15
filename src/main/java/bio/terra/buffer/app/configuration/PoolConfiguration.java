package bio.terra.buffer.app.configuration;

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

  // Whether to read config file from system file.
  private boolean readConfigFromSystemFile;

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

  public boolean getReadConfigFromSystemFile() {
    return readConfigFromSystemFile;
  }

  public void setReadConfigFromSystemFile(boolean readConfigFromSystemFile) {
    this.readConfigFromSystemFile = readConfigFromSystemFile;
  }
}
