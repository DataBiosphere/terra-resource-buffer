package bio.terra.buffer.service.pool;

import bio.terra.buffer.common.exception.InvalidPoolConfigException;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;

/**
 * Validates GCP resource config. Because Resource Buffer Service creates customized network for all
 * projects, this requires:
 *
 * <ul>
 *   <li>Billing account is present.
 * </ul>
 */
public class GcpResourceConfigValidator implements ResourceConfigValidator {
  @Override
  public void validate(ResourceConfig config) {
    GcpProjectConfig gcpProjectConfig = config.getGcpProjectConfig();
    if (gcpProjectConfig.getBillingAccount() == null
        || gcpProjectConfig.getBillingAccount().isEmpty()) {
      throw new InvalidPoolConfigException(
          String.format("Missing billing account for config: %s", config.getConfigName()));
    }
  }
}
