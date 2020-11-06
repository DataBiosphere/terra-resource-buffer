package bio.terra.rbs.service.pool;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

public class ResourceConfigValidatorTest extends BaseUnitTest {
  private static GcpProjectConfig newValidGcpProjectConfig() {
    return new GcpProjectConfig()
        .billingAccount("123")
        .addEnabledApisItem("compute.googleapis.com")
        .addEnabledApisItem("storage-component.googleapis.com");
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
  public void testValidateGcpConfig_missingRequiredApis() {
    ResourceConfig resourceConfig =
        new ResourceConfig()
            .configName("testConfig")
            .gcpProjectConfig(
                newValidGcpProjectConfig().enabledApis(ImmutableList.of("foo.googleapis.com")));
    InvalidPoolConfigException exception =
        assertThrows(
            InvalidPoolConfigException.class,
            () -> new GcpResourceConfigValidator().validate(resourceConfig));
    assertTrue(exception.getMessage().contains("Missing required enabledApis"));
  }
}
