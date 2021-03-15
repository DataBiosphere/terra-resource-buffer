package bio.terra.buffer.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      // Logging components & configs
      "bio.terra.common.logging",
      // Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Tracing-related components & configs
      "bio.terra.common.tracing",
      // Scan all service-specific packages beneath the current package
      "bio.terra.buffer"
    })
public class Main {
  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
