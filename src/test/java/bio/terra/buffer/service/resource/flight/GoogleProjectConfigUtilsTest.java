package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.Network;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GoogleProjectConfigUtilsTest extends BaseUnitTest {

  @Test
  public void userPrivateGoogleAccess_test() {
    GcpProjectConfig gcpProjectConfig =
        new GcpProjectConfig().network(new Network().enablePrivateGoogleAccess(false));
    assertFalse(GoogleProjectConfigUtils.usePrivateGoogleAccess(gcpProjectConfig));

    gcpProjectConfig.setNetwork(gcpProjectConfig.getNetwork().enableNetworkMonitoring(true));
    assertTrue(GoogleProjectConfigUtils.usePrivateGoogleAccess(gcpProjectConfig));
  }

  @Test
  public void getSecurityGroup_test() {
    GcpProjectConfig gcpProjectConfig = new GcpProjectConfig();
    assertTrue(GoogleProjectConfigUtils.getSecurityGroup(gcpProjectConfig).isEmpty());

    gcpProjectConfig.setSecurityGroup("   ");
    assertTrue(GoogleProjectConfigUtils.getSecurityGroup(gcpProjectConfig).isEmpty());

    gcpProjectConfig.setSecurityGroup("secGroup");
    assertEquals(
        gcpProjectConfig.getSecurityGroup(),
        GoogleProjectConfigUtils.getSecurityGroup(gcpProjectConfig).orElse(Strings.EMPTY));
  }
}
