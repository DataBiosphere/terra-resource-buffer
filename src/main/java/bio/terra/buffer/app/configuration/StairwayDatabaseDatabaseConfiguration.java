package bio.terra.buffer.app.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value = StairwayDatabaseProperties.class)
public class StairwayDatabaseDatabaseConfiguration extends BaseDatabaseConfiguration {
  public StairwayDatabaseDatabaseConfiguration(StairwayDatabaseProperties databaseProperties) {
    super(databaseProperties);
  }
}
