package bio.terra.buffer.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import org.junit.jupiter.api.Test;

public class PoolIdTest extends BaseUnitTest {
  @Test
  public void storeAndRetrieveFromFlightMap() throws Exception {
    PoolId id = PoolId.create("poolId");
    FlightMap flightMap = new FlightMap();
    flightMap.put("key", 123);
    id.store(flightMap);
    assertEquals(id, PoolId.retrieve(flightMap));
  }

  @Test
  public void family_stripsVersionSuffix() {
    assertEquals("cwb_ws_prod", PoolId.create("cwb_ws_prod_v9").family());
  }

  @Test
  public void family_stripsLargeVersion() {
    assertEquals("vpc_sc", PoolId.create("vpc_sc_v13").family());
  }

  @Test
  public void family_stripsSingleDigitVersion() {
    assertEquals("datarepo", PoolId.create("datarepo_v1").family());
  }

  @Test
  public void family_multipleUnderscores() {
    assertEquals("datarepo_fakeprod", PoolId.create("datarepo_fakeprod_v1").family());
  }

  @Test
  public void family_noVersionSuffix() {
    assertEquals("no_version_suffix", PoolId.create("no_version_suffix").family());
  }
}
