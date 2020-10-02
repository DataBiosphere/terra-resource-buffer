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
  /** Pool schema file name should be the same name for all environments. */
  public static final String POOL_SCHEMA_NAME = "pool_schema.yml";
  /** Resource configs folder name should be the same name for all environments. */
  public static final String RESOURCE_CONFIG_SUB_DIR_NAME = "resource-config";

  // The path to have pool and resource config files.
  private String configPath;

  // Whether to update pool from pool config when server start.
  private boolean updatePoolOnStart;

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
}
