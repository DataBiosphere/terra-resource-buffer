package bio.terra.buffer.service.stackdriver;

import bio.terra.buffer.app.configuration.StackdriverConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A component for setting up Stackdriver stats and tracing exporting, copied from Janitor code. */
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
      StackdriverTraceExporter.createAndRegister(StackdriverTraceConfiguration.builder().build());
    } catch (IOException e) {
      throw new RuntimeException("Unable to initialize Stackdriver exporting.", e);
    }

    TraceConfig globalTraceConfig = Tracing.getTraceConfig();
    globalTraceConfig.updateActiveTraceParams(
        globalTraceConfig.getActiveTraceParams().toBuilder()
            .setSampler(
                Samplers.probabilitySampler(stackdriverConfiguration.getTraceSampleProbability()))
            .build());
  }
}
