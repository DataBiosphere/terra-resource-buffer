package bio.terra.buffer.app.configuration;

import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableConfigurationProperties(value = BufferDatabaseProperties.class)
@EnableTransactionManagement
public class BufferDatabaseConfiguration {

  private DataSource dataSource;
  private final BufferDatabaseProperties databaseProperties;

  public BufferDatabaseConfiguration(BufferDatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
    dataSource = DataSourceInitializer.initializeDataSource(databaseProperties);
  }

  public boolean isRecreateDbOnStart() {
    return databaseProperties.isRecreateDbOnStart();
  }

  public boolean isUpdateDbOnStart() {
    return databaseProperties.isUpdateDbOnStart();
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(getDataSource());
  }
}
