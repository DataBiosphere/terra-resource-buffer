package bio.terra.rbs.service.pool;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import org.junit.jupiter.api.Test;

public class ResourceConfigValidatorTest extends BaseUnitTest {
  @Test
  public void testValidateGcpConfig_success() {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("testConfig")
            .gcpProjectConfig(
                new GcpProjectConfig()
                    .billingAccount("123")
                    .addEnabledApisItem("compute.googleapis.com"));
    new GcpResourceConfigValidator().validate(resourceConfig);
  }

  @Test
  public void testValidateGcpConfig_missingBillingAccount() {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("testConfig")
            .gcpProjectConfig(new GcpProjectConfig().addEnabledApisItem("compute.googleapis.com"));
    assertThrows(
        InvalidPoolConfigException.class,
        () -> new GcpResourceConfigValidator().validate(resourceConfig));
  }

  @Test
  public void testValidateGcpConfig_missingRequiredApis() {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("testConfig")
            .gcpProjectConfig(new GcpProjectConfig().billingAccount("123"));
    assertThrows(
        InvalidPoolConfigException.class,
        () -> new GcpResourceConfigValidator().validate(resourceConfig));
  }
}
