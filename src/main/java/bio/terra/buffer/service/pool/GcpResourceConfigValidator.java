package bio.terra.buffer.service.pool;

import static bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator.MAX_LENGTH_GCP_PROJECT_ID;
import static bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator.MAX_LENGTH_GCP_PROJECT_ID_PREFIX;
import static bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator.RANDOM_ID_SIZE;

import bio.terra.buffer.common.exception.InvalidPoolConfigException;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ProjectIdSchema;
import bio.terra.buffer.generated.model.ProjectIdSchema.SchemeEnum;
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
    if (gcpProjectConfig.getProjectIdSchema().getScheme()
            == ProjectIdSchema.SchemeEnum.TWO_WORDS_NUMBER
        && (gcpProjectConfig.getProjectIdSchema().getPrefix().length()
            > MAX_LENGTH_GCP_PROJECT_ID_PREFIX)) {
      throw new InvalidPoolConfigException(
          String.format(
              "Project id prefix is too long for TWO_WORDS_NUMBER naming scheme: %s",
              config.getConfigName()));
    }
    if (gcpProjectConfig.getProjectIdSchema().getScheme() == SchemeEnum.RANDOM_CHAR
        && (gcpProjectConfig.getProjectIdSchema().getPrefix().length() + RANDOM_ID_SIZE
            > MAX_LENGTH_GCP_PROJECT_ID)) {
      throw new InvalidPoolConfigException(
          String.format(
              "Project id prefix is too long for RANDOM_CHAR naming scheme: %s",
              config.getConfigName()));
    }
  }
}
