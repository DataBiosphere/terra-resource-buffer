package bio.terra.buffer.app.configuration;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PrometheusConfiguration {

    /**
     * Wires in OpenCensus's stats underneath the Prometheus registry that
     * Micrometer exposes via Spring Boot's "Actuator" pattern.
     * <p>
     * Spring Boot has built-in Prometheus support--it will handle
     * initialization and the endpoint automatically--but exclusively in
     * concert with the Micrometer library. This app is already instrumented
     * with OpenCensus, though, and we don't want to re-instrument with
     * Micrometer instead.
     * </p>
     * <p>
     * The solution: OpenCensus will happily work directly with a raw
     * Prometheus {@link io.prometheus.client.CollectorRegistry}, so we have
     * it register with the underlying instance that Micrometer and Spring Boot
     * are sharing to power the automatic endpoint.
     * </p>
     * <p>
     * @see PrometheusStatsCollector#createAndRegister(PrometheusStatsConfiguration)
     * @see PrometheusMeterRegistry#getPrometheusRegistry()
     * </p>
     */
    @Bean
    public MeterRegistryCustomizer<PrometheusMeterRegistry> registerOpenCensus() {
        return (micrometerRegistry) -> PrometheusStatsCollector.createAndRegister(
                PrometheusStatsConfiguration.builder()
                        .setRegistry(micrometerRegistry.getPrometheusRegistry())
                        .build()
        );
    }
}
