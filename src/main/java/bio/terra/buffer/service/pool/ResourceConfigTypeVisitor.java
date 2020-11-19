package bio.terra.buffer.service.pool;

import bio.terra.buffer.common.ResourceConfigVisitor;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ResourceConfigTypeVisitor implements ResourceConfigVisitor<Optional<ResourceType>> {
  @Override
  public Optional<ResourceType> visit(GcpProjectConfig resource) {
    return Optional.of(ResourceType.GOOGLE_PROJECT);
  }

  @Override
  public Optional<ResourceType> noResourceVisited(ResourceConfig resource) {
    return Optional.empty();
  }
}
