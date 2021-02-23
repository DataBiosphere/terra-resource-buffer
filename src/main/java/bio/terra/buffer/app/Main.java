package bio.terra.buffer.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      // Scan for Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Scan all service-specific packages beneath the current package
      "bio.terra.buffer"
    })
@ComponentScan(basePackages = {"bio.terra.buffer", "bio.terra.common"})
public class Main {
  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
