package bio.terra.buffer.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configurations for running Buffer Services in Kubernetes and support multi-instance Stairway. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.kubernetes")
public class KubernetesConfiguration {
  // The pod that host the Buffer service app.
  private String podNameFilter;

  // Name of the Kubernetes pods we are running in. We uses common library uses this to list all
  // pods in the deployment, then uses podNameFilter to find the pod that host the app.
  // e.g.m podName can gives us buffer-service, buffer-cloudsql-proxy, and we use podNameFilter to
  // find buffer-service.
  private String podName;

  // Whether the app is running in Kubernetes.
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
