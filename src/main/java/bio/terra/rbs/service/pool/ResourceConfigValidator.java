package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.ResourceConfig;

/**
 * Validate {@link ResourceConfig} is valid.
 *
 * <p>Throws a RuntimeException if validation does not succeed.
 */
public interface ResourceConfigValidator {
  void validate(ResourceConfig config);
}
