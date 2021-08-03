package bio.terra.buffer.service.pool;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.common.exception.InvalidPoolConfigException;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ProjectIdSchema;
import bio.terra.buffer.generated.model.ResourceConfig;
import org.junit.jupiter.api.Test;

public class ResourceConfigValidatorTest extends BaseUnitTest {
  private static GcpProjectConfig newValidGcpProjectConfig() {
    return new GcpProjectConfig()
        .billingAccount("123")
        .projectIdSchema(
            new ProjectIdSchema().prefix("prefix").scheme(ProjectIdSchema.SchemeEnum.RANDOM_CHAR));
  }

  @Test
  public void testValidateGcpConfig_success() {
    ResourceConfig resourceConfig =
        new ResourceConfig().configName("testConfig").gcpProjectConfig(newValidGcpProjectConfig());
    new GcpResourceConfigValidator().validate(resourceConfig);
  }

  @Test
  public void testValidateGcpConfig_missingBillingAccount() {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("testConfig")
            .gcpProjectConfig(newValidGcpProjectConfig().billingAccount(""));
    InvalidPoolConfigException exception =
        assertThrows(
            InvalidPoolConfigException.class,
            () -> new GcpResourceConfigValidator().validate(resourceConfig));
    assertTrue(exception.getMessage().contains("Missing billing account"));
  }

  @Test
  public void testValidateGcpConfig_prefixTooLong() {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("testConfig")
            .gcpProjectConfig(
                newValidGcpProjectConfig()
                    .projectIdSchema(
                        new ProjectIdSchema()
                            .prefix("prefixlongerthan12characters")
                            .scheme(ProjectIdSchema.SchemeEnum.TWO_WORDS_NUMBER)));
    InvalidPoolConfigException exception =
        assertThrows(
            InvalidPoolConfigException.class,
            () -> new GcpResourceConfigValidator().validate(resourceConfig));
    assertTrue(
        exception
            .getMessage()
            .contains("Project id prefix is too long for TWO_WORDS_NUMBER naming scheme"));
  }
}
