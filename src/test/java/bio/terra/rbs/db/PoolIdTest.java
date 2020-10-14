package bio.terra.rbs.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.stairway.FlightMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class PoolIdTest extends BaseUnitTest {
  @Test
  public void serialize() throws Exception {
    assertEquals(
        "[\"bio.terra.rbs.db.AutoValue_PoolId\",{\"id\":\"id\"}]",
        new ObjectMapper().writeValueAsString(PoolId.create("id")));
  }

  @Test
  public void deserialize() throws Exception {
    assertEquals(
        PoolId.create("id"),
        new ObjectMapper()
            .readValue("[\"bio.terra.rbs.db.AutoValue_PoolId\",{\"id\":\"id\"}]", PoolId.class));
  }

  @Test
  public void storeAndRetrieveFromFlightMap() throws Exception {
    PoolId id = PoolId.create("poolId");
    FlightMap flightMap = new FlightMap();
    id.store(flightMap);
    assertEquals(id, PoolId.retrieve(flightMap));
  }
}
