package bio.terra.rbs.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Unit tests to verify {@link PoolId} is serializable/deserializable by Jackson. */
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
}
