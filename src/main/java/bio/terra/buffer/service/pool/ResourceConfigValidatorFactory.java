package bio.terra.buffer.service.pool;

import bio.terra.buffer.common.ResourceConfigVisitor;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.generated.model.ResourceConfig;

/** Factory to return {ResourceConfigValidator} by {@link ResourceConfig}. */
public class ResourceConfigValidatorFactory {
  public static ResourceConfigValidator getValidator(ResourceConfig resourceConfig) {
    ResourceType type =
        ResourceConfigVisitor.visit(resourceConfig, new ResourceConfigTypeVisitor()).get();
    if (type.equals(ResourceType.GOOGLE_PROJECT)) {
      return new GcpResourceConfigValidator();
    } else {
      return new AlwaysPassValidator();
    }
  }
}
