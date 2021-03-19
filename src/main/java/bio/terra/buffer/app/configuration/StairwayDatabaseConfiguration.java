package bio.terra.buffer.app.configuration;

import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.stairway.db")
public class StairwayDatabaseConfiguration {
  private DataSource dataSource;
  private final StairwayDatabaseProperties databaseProperties;

  public StairwayDatabaseConfiguration(StairwayDatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
    dataSource = DataSourceInitializer.initializeDataSource(databaseProperties);
  }

  public DataSource getDataSource() {
    return dataSource;
  }
}
