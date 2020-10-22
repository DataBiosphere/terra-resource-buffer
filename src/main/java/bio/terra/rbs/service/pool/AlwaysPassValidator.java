package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.ResourceConfig;

/** The default validator which does not no restrictions. */
public class AlwaysPassValidator implements ResourceConfigValidator {
  @Override
  public void validate(ResourceConfig config) {
    // Always pass.
  }
}
