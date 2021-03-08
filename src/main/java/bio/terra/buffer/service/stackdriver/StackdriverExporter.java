package bio.terra.buffer.service.stackdriver;

import bio.terra.buffer.app.configuration.StackdriverConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A component for setting up Stackdriver stats exporting, copied from Janitor code. */
@Component
public class StackdriverExporter {
  private final Logger logger = LoggerFactory.getLogger(StackdriverExporter.class);

  private final StackdriverConfiguration stackdriverConfiguration;

  @Autowired
  public StackdriverExporter(StackdriverConfiguration stackdriverConfiguration) {
    this.stackdriverConfiguration = stackdriverConfiguration;
  }

  public void initialize() {
    logger.info("Stackdriver enabled: {}.", stackdriverConfiguration.isEnabled());
    if (!stackdriverConfiguration.isEnabled()) {
      return;
    }
    try {
      StackdriverStatsExporter.createAndRegister();
    } catch (IOException e) {
      logger.error("Unable to initialize Stackdriver exporting.", e);
    }
  }
}
