package bio.terra.rbs.service.pool;

import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Validates GCP resource config. Because RBS creates customized network for all projects, this
 * requires:
 *
 * <ul>
 *   <li>Billing account is present.
 *   <li>compute.googleapis.com need to be enabled.
 * </ul>
 */
public class GcpResourceConfigValidator implements ResourceConfigValidator {
  /** List of services required to be enabled. */
  private static final List<String> REQUIRED_SERVICES =
      ImmutableList.of("compute.googleapis.com", "storage-component.googleapis.com");

  @Override
  public void validate(ResourceConfig config) {
    GcpProjectConfig gcpProjectConfig = config.getGcpProjectConfig();
    if (gcpProjectConfig.getBillingAccount() == null
        || gcpProjectConfig.getBillingAccount().isEmpty()) {
      throw new InvalidPoolConfigException(
          String.format("Missing billing account for config: %s", config.getConfigName()));
    }

    if (gcpProjectConfig.getEnabledApis() == null
        || !gcpProjectConfig.getEnabledApis().containsAll(REQUIRED_SERVICES)) {
      throw new InvalidPoolConfigException(
          String.format("Missing required enabledApis for config: %s", config.getConfigName()));
    }
  }
}
