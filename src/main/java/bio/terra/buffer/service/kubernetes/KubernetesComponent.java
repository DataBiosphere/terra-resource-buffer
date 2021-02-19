package bio.terra.buffer.service.kubernetes;

import bio.terra.buffer.app.configuration.KubernetesConfiguration;
import bio.terra.common.kubernetes.KubeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A Spring Component for exposing an initialized {@link KubeService}. */
@Component
public class KubernetesComponent {
  private final KubeService kubeService;
  private final KubernetesConfiguration kubernetesConfiguration;

  @Autowired
  public KubernetesComponent(KubernetesConfiguration kubernetesConfiguration) {
    this.kubernetesConfiguration = kubernetesConfiguration;
    this.kubeService =
        new KubeService(
            kubernetesConfiguration.getPodName(),
            kubernetesConfiguration.isInKubernetes(),
            kubernetesConfiguration.getPodNameFilter());
  }

  public KubeService get() {
    return kubeService;
  }

  /** Returns {@link true} if in Kubernetes. */
  public boolean isInKubernetes() {
    return kubernetesConfiguration.isInKubernetes();
  }
}
