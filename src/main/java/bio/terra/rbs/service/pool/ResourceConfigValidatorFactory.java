package bio.terra.rbs.service.pool;

import bio.terra.rbs.common.ResourceConfigVisitor;
import bio.terra.rbs.common.ResourceType;
import bio.terra.rbs.generated.model.ResourceConfig;
import org.springframework.stereotype.Component;

/** Factory to return {ResourceConfigValidator} by {@link ResourceConfig}. */
@Component
public class ResourceConfigValidatorFactory {
  public static ResourceConfigValidator getValidator(ResourceConfig resourceConfig) {
    ResourceType type =
        ResourceConfigVisitor.visit(resourceConfig, new ResourceConfigTypeVisitor()).get();
    if (type.equals(ResourceType.GOOGLE_PROJECT)) {
      return new GcpResourceConfigValidator();
    } else {
      return new DefaultResourceConfigValidator();
    }
  }
}
