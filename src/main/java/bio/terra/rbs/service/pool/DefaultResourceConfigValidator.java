package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.ResourceConfig;

/** The default validator which does not require any restrictions on the config. */
public class DefaultResourceConfigValidator implements ResourceConfigValidator {
  @Override
  public void validate(ResourceConfig config) {
    // Always pass.
  }
}
