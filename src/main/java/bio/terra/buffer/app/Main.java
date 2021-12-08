package bio.terra.buffer.app;

import bio.terra.common.logging.LoggingInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      "bio.terra.common.db",
      "bio.terra.common.kubernetes",
      "bio.terra.common.stairway",
      // Logging components & configs
      "bio.terra.common.logging",
      // Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Tracing-related components & configs
      "bio.terra.common.tracing",
      // Metrics exporting components & configs
      "bio.terra.common.prometheus",
      // Scan all service-specific packages beneath the current package
      "bio.terra.buffer"
    })
public class Main {
  public static void main(String[] args) {
    new SpringApplicationBuilder(Main.class).initializers(new LoggingInitializer()).run(args);
  }
}
