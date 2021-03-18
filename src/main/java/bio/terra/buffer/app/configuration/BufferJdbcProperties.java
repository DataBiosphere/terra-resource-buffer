package bio.terra.buffer.app.configuration;

import bio.terra.common.db.JdbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "buffer.db")
public class BufferJdbcProperties extends JdbcProperties {}
