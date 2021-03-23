package bio.terra.buffer.app.configuration;

import bio.terra.common.db.BaseDatabaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buffer.db")
public class BufferDatabaseProperties extends BaseDatabaseProperties {
  // These properties control code in the StartupInitializer. We would not use these in production,
  // but they are handy to set for development and testing. There are only three interesting states:
  // 1. recreateDbOnStart is true; updateDbOnStart is irrelevant - initialize and recreate an empty
  // database
  // 2. recreateDbOnStart is false; updateDbOnStart is true - apply changesets to an existing
  // database
  // 3. recreateDbOnStart is false; updateDbOnStart is false - do nothing to the database
  private boolean recreateDbOnStart;
  private boolean updateDbOnStart;

  public boolean isRecreateDbOnStart() {
    return recreateDbOnStart;
  }

  public void setRecreateDbOnStart(boolean recreateDbOnStart) {
    this.recreateDbOnStart = recreateDbOnStart;
  }

  public boolean isUpdateDbOnStart() {
    return updateDbOnStart;
  }

  public void setUpdateDbOnStart(boolean updateDbOnStart) {
    this.updateDbOnStart = updateDbOnStart;
  }
}
