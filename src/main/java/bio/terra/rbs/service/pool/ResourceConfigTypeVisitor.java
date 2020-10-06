package bio.terra.rbs.service.pool;

import bio.terra.rbs.common.ResourceConfigVisitor;
import bio.terra.rbs.db.ResourceType;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
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
