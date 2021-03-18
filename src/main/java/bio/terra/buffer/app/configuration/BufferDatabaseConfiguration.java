package bio.terra.buffer.app.configuration;

import bio.terra.common.db.DatabaseConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(value = BufferDatabaseProperties.class)
public class BufferDatabaseConfiguration extends DatabaseConfiguration {
  public BufferDatabaseConfiguration(BufferDatabaseProperties bufferDatabaseProperties) {
    super(bufferDatabaseProperties);
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(getDataSource());
  }
}
