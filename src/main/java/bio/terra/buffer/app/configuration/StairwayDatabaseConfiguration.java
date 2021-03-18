package bio.terra.buffer.app.configuration;

import static bio.terra.buffer.app.configuration.BeanNames.STAIRWAY_DB_DATA_SOURCE;

import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value = StairwayDatabaseProperties.class)
public class StairwayDatabaseConfiguration {
  private final StairwayDatabaseProperties databaseProperties;

  public StairwayDatabaseConfiguration(StairwayDatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
  }

  @Bean(STAIRWAY_DB_DATA_SOURCE)
  public DataSource getStairwayDbDataSource() {
    return DataSourceInitializer.initializeDataSource(databaseProperties);
  }
}
