package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.ResourceConfig;

/** Resource config validator to make sure config is valid. */
public interface ResourceConfigValidator {
  void validate(ResourceConfig config);
}
