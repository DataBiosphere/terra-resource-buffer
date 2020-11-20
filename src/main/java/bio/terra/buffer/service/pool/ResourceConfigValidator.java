package bio.terra.buffer.service.pool;

import bio.terra.buffer.generated.model.ResourceConfig;

/**
 * Validate {@link ResourceConfig} is valid.
 *
 * <p>Throws a RuntimeException if validation does not succeed.
 */
public interface ResourceConfigValidator {
  void validate(ResourceConfig config);
}
