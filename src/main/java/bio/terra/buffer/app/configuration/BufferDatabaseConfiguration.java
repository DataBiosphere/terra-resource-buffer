package bio.terra.buffer.app.configuration;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_DB_DATA_SOURCE;

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
  private final BufferDatabaseProperties databaseProperties;

  public BufferDatabaseConfiguration(BufferDatabaseProperties databaseProperties) {
    System.out.println("~~~~~~~~~~");
    System.out.println(databaseProperties.getUri());
    this.databaseProperties = databaseProperties;
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
