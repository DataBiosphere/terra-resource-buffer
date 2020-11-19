package bio.terra.buffer.common;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;

/** An interface for switching on the different resource types within a {@link ResourceConfig}. */
public interface ResourceConfigVisitor<R> {
  R visit(GcpProjectConfig resource);

  R noResourceVisited(ResourceConfig resource);

  static <R> R visit(ResourceConfig resource, ResourceConfigVisitor<R> visitor) {
    if (resource.getGcpProjectConfig() != null) {
      return visitor.visit(resource.getGcpProjectConfig());
    } else {
      return visitor.noResourceVisited(resource);
    }
  }
}
