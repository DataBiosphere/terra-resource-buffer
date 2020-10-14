package bio.terra.rbs.db;

import bio.terra.stairway.FlightMap;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;

/** Wraps the id in db pool table. */
@AutoValue
@JsonSerialize(as = PoolId.class)
@JsonDeserialize(builder = AutoValue_PoolId.Builder.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
public abstract class PoolId {
  private static final String POOL_ID_MAP_KEY = "PoolId";

  @JsonProperty("id")
  public abstract String id();

  public static PoolId create(String id) {
    return new AutoValue_PoolId.Builder().id(id).build();
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
  public FlightMap store(FlightMap map) {
    map.put(POOL_ID_MAP_KEY, id());
    return map;
  }

  /** Builder for {@link PoolId}. */
  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder id(String value);

    public abstract PoolId build();
  }
}
