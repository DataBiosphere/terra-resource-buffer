package bio.terra.buffer.service.prometheus;

import bio.terra.buffer.app.configuration.PrometheusConfiguration;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A component for setting up Prometheus stats exporting, copied from StackdriverExporter. */
@Component
public class PrometheusExporter {
  private final Logger logger = LoggerFactory.getLogger(PrometheusExporter.class);

  private final PrometheusConfiguration prometheusConfiguration;

  @Autowired
  public PrometheusExporter(PrometheusConfiguration prometheusConfiguration) {
    this.prometheusConfiguration = prometheusConfiguration;
  }

  public void initialize() {
    logger.info("Prometheus enabled: {}.", prometheusConfiguration.isEnabled());
    if (!prometheusConfiguration.isEnabled()) {
      return;
    }
    try {
      PrometheusStatsCollector.createAndRegister();
    } catch (IllegalArgumentException e) {
      logger.error("Opencensus prometheus exporter already registered.", e);
    }
  }
}
