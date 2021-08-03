package bio.terra.buffer.service.pool;

import static bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator.MAX_LENGTH_GCP_PROJECT_ID_PREFIX;

import bio.terra.buffer.common.exception.InvalidPoolConfigException;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ProjectIdSchema;
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
    if (gcpProjectConfig
            .getProjectIdSchema()
            .getScheme()
            .equals(ProjectIdSchema.SchemeEnum.TWO_WORDS_NUMBER)
        && gcpProjectConfig.getProjectIdSchema().getPrefix().length()
            > MAX_LENGTH_GCP_PROJECT_ID_PREFIX) {
      throw new InvalidPoolConfigException(
          String.format(
              "Project id prefix is too long for TWO_WORDS_NUMBER naming scheme: %s",
              config.getConfigName()));
    }
  }
}
