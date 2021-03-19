package bio.terra.buffer.app.configuration;

import bio.terra.common.db.BaseDatabaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.stairway.db")
public class StairwayDatabaseProperties extends BaseDatabaseProperties {}
