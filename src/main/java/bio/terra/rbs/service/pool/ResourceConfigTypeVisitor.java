package bio.terra.rbs.service.pool;

import bio.terra.rbs.common.ResourceConfigVisitor;
import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.db.ResourceType;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class ResourceConfigTypeVisitor implements ResourceConfigVisitor<ResourceType> {
  @Override
  public ResourceType visit(GcpProjectConfig resource) {
    return ResourceType.GOOGLE_PROJECT;
  }

  @Override
  public ResourceType noResourceVisited(ResourceConfig resource) {
    throw new InvalidPoolConfigException(String.format("Invalid resource config for %s", resource));
  }

  public ResourceType accept(ResourceConfig resourceConfig) {
    return ResourceConfigVisitor.visit(resourceConfig, this);
  }
}
