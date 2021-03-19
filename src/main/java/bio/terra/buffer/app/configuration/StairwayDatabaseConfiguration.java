package bio.terra.buffer.app.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value = StairwayDatabaseProperties.class)
public class StairwayDatabaseConfiguration extends BaseDataBaseConfiguration {
  public StairwayDatabaseConfiguration(StairwayDatabaseProperties databaseProperties) {
    super(databaseProperties);
  }
}
