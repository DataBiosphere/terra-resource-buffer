package bio.terra.buffer.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.kubernetes")
public class KubernetesConfiguration {
  private String podNameFilter = "buffer-service";

  private String podName;

  private boolean inKubernetes;

  public String getPodName() {
    return podName;
  }

  public void setPodName(String podName) {
    this.podName = podName;
  }

  public String getPodNameFilter() {
    return podNameFilter;
  }

  public void setPodNameFilter(String podNameFilter) {
    this.podNameFilter = podNameFilter;
  }

  public boolean isInKubernetes() {
    return inKubernetes;
  }

  public void setInKubernetes(boolean inKubernetes) {
    this.inKubernetes = inKubernetes;
  }
}
