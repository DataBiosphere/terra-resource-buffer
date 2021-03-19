package bio.terra.buffer.app.configuration;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_JDBC_TEMPLATE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableConfigurationProperties(value = BufferDatabaseProperties.class)
@EnableTransactionManagement
public class BufferDatabaseConfiguration extends BaseDataBaseConfiguration {
  public BufferDatabaseConfiguration(BufferDatabaseProperties databaseProperties) {
    super(databaseProperties);
  }

  @Bean(BUFFER_JDBC_TEMPLATE)
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
    return new NamedParameterJdbcTemplate(getDataSource());
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(getDataSource());
  }
}
