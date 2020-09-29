package bio.terra.rbs.common;

import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;

/** An interface for switching on the different resource types within a {@link ResourceConfig}. */
public interface ResourceConfigVisitor<R> {
  R visit(GcpProjectConfig resource);

  R noResourceVisited(ResourceConfig config);

  static <R> R visit(ResourceConfig config, ResourceConfigVisitor<R> visitor) {
    if (config.getGcpProjectConfig() != null) {
      return visitor.visit(config.getGcpProjectConfig());
    } else {
      return visitor.noResourceVisited(config);
    }
  }
}
