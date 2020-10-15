package bio.terra.rbs.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.stairway.FlightMap;
import org.junit.jupiter.api.Test;

public class PoolIdTest extends BaseUnitTest {
  @Test
  public void storeAndRetrieveFromFlightMap() throws Exception {
    PoolId id = PoolId.create("poolId");
    FlightMap flightMap = new FlightMap();
    id.store(flightMap);
    assertEquals(id, PoolId.retrieve(flightMap));
  }
}
