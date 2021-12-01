package bio.terra.buffer.service.prometheus;

import bio.terra.buffer.app.configuration.PrometheusConfiguration;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.prometheus.client.exporter.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;

/**
 * A component that automatically runs a Prometheus endpoint server on a different port,
 * exporting stats from OpenCensus.
 */
@Component
public class PrometheusEndpoint {
    private final Logger logger = LoggerFactory.getLogger(PrometheusEndpoint.class);
    private final PrometheusConfiguration prometheusConfiguration;

    private HTTPServer prometheusServer;

    @Autowired
    public PrometheusEndpoint(PrometheusConfiguration prometheusConfiguration) {
        this.prometheusConfiguration = prometheusConfiguration;
    }

    @PostConstruct
    private void startEndpointServer() {
        logger.info("Prometheus enabled: {}.", prometheusConfiguration.isEnabled());
        if (!prometheusConfiguration.isEnabled()) {
            return;
        }
        try {
            PrometheusStatsCollector.createAndRegister();
        } catch (IllegalArgumentException e) {
            logger.error("OpenCensus Prometheus Collector already registered.", e);
        }
        try {
            prometheusServer = new HTTPServer(prometheusConfiguration.getPort());
            logger.info("Prometheus server started: port {}", prometheusConfiguration.getPort());
        } catch (IOException e) {
            logger.error("Prometheus server error on startup.", e);
        }
    }

    @PreDestroy
    private void stopEndpointServer() {
        if (Objects.isNull(prometheusServer)) {
            return;
        }
        prometheusServer.close();
        logger.info("Prometheus server stopped");
    }
}
