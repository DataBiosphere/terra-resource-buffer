package bio.terra.buffer.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.stackdriver")
public class StackdriverConfiguration {
  /** Whether to enable stackdriver metrics & tracing collection. */
  private boolean enabled = true;

  /** The probability to record a trace. Should be between 0 and 1. */
  private double traceSampleProbability = 0.1;

  public double getTraceSampleProbability() {
    return traceSampleProbability;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setTraceSampleProbability(double traceSampleProbability) {
    this.traceSampleProbability = traceSampleProbability;
  }
}
