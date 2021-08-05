package bio.terra.buffer.service.resource.flight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.Network;
import org.junit.jupiter.api.Test;

public class GoogleProjectConfigUtilsTest extends BaseUnitTest {

  @Test
  public void userPrivateGoogleAccess() throws Exception {
    GcpProjectConfig gcpProjectConfig =
        new GcpProjectConfig().network(new Network().enablePrivateGoogleAccess(false));
    assertFalse(GoogleProjectConfigUtils.usePrivateGoogleAccess(gcpProjectConfig));

    gcpProjectConfig.setNetwork(gcpProjectConfig.getNetwork().enableNetworkMonitoring(true));
    assertTrue(GoogleProjectConfigUtils.usePrivateGoogleAccess(gcpProjectConfig));
  }
}
