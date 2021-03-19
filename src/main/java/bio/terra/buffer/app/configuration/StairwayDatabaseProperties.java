package bio.terra.buffer.app.configuration;

import bio.terra.common.db.BaseDatabaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buffer.stairway.db")
public class StairwayDatabaseProperties extends BaseDatabaseProperties {}
