package bio.terra.buffer.app.configuration;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_DB_DATA_SOURCE;

import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(value = BufferDatabaseProperties.class)
public class BufferDatabaseConfiguration {
  private final BufferDatabaseProperties databaseProperties;
  // These properties control code in the StartupInitializer. We would not use these in production,
  // but they are handy to set for development and testing. There are only three interesting states:
  // 1. recreateDbOnStart is true; updateDbOnStart is irrelevant - initialize and recreate an empty
  // database
  // 2. recreateDbOnStart is false; updateDbOnStart is true - apply changesets to an existing
  // database
  // 3. recreateDbOnStart is false; updateDbOnStart is false - do nothing to the database
  private boolean recreateDbOnStart;
  private boolean updateDbOnStart;

  public BufferDatabaseConfiguration(BufferDatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
  }

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

  @Bean(BUFFER_DB_DATA_SOURCE)
  public DataSource getBufferDbDataSource() {
    return DataSourceInitializer.initializeDataSource(databaseProperties);
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(getBufferDbDataSource());
  }
}
