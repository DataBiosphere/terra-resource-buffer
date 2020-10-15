package bio.terra.rbs.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.stairway.FlightMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ResourceIdTest extends BaseUnitTest {
  @Test
  public void storeAndRetrieveFromFlightMap() throws Exception {
    ResourceId id = ResourceId.create(UUID.randomUUID());
    FlightMap flightMap = new FlightMap();
    id.store(flightMap);
    assertEquals(id, ResourceId.retrieve(flightMap));
  }
}
