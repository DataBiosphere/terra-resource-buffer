package bio.terra.rbs.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.stairway.FlightMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ResourceIdTest extends BaseUnitTest {
  @Test
  public void serialize() throws Exception {
    UUID id = UUID.randomUUID();
    assertEquals(
        "[\"bio.terra.rbs.db.AutoValue_ResourceId\",{\"id\":\"" + id.toString() + "\"}]",
        new ObjectMapper().writeValueAsString(ResourceId.create(id)));
  }

  @Test
  public void deserialize() throws Exception {
    UUID id = UUID.randomUUID();
    assertEquals(
        ResourceId.create(id),
        new ObjectMapper()
            .readValue(
                "[\"bio.terra.rbs.db.AutoValue_ResourceId\",{\"id\":\"" + id.toString() + "\"}]",
                ResourceId.class));
  }

  @Test
  public void storeAndRetrieveFromFlightMap() throws Exception {
    ResourceId id = ResourceId.create(UUID.randomUUID());
    FlightMap flightMap = new FlightMap();
    id.store(flightMap);
    assertEquals(id, ResourceId.retrieve(flightMap));
  }
}
