package bio.terra.rbs.common;

import bio.terra.stairway.FlightMap;
import com.google.auto.value.AutoValue;

/** The unique identifier for every Pool. */
@AutoValue
public abstract class PoolId {
  private static final String POOL_ID_MAP_KEY = "PoolId";

  public abstract String id();

  public static PoolId create(String id) {
    return new AutoValue_PoolId(id);
  }

  @Override
  public String toString() {
    return id();
  }

  /** Retrieve and construct a PoolId form {@link FlightMap}. */
  public static PoolId retrieve(FlightMap map) {
    return PoolId.create(map.get(POOL_ID_MAP_KEY, String.class));
  }

  /** Stores PoolId value in {@link FlightMap}. */
  public void store(FlightMap map) {
    map.put(POOL_ID_MAP_KEY, id());
  }
}
