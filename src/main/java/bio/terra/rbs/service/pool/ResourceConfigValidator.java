package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.ResourceConfig;

/** Validate {@link ResourceConfig} is valid. */
public interface ResourceConfigValidator {
  void validate(ResourceConfig config);
}
