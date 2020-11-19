package bio.terra.buffer.service.pool;

import bio.terra.buffer.generated.model.ResourceConfig;

/** The validator which does not no restrictions. */
public class AlwaysPassValidator implements ResourceConfigValidator {
  @Override
  public void validate(ResourceConfig config) {
    // Always pass.
  }
}
