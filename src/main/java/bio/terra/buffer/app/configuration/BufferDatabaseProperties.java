package bio.terra.buffer.app.configuration;

import bio.terra.common.db.DatabaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buffer.db")
public class BufferDatabaseProperties extends DatabaseProperties {}
