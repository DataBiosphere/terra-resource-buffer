package bio.terra.rbs.db;

import bio.terra.stairway.FlightMap;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;
import java.util.UUID;

/** Wraps the id in db pool table. */
@AutoValue
@JsonSerialize(as = ResourceId.class)
@JsonDeserialize(builder = AutoValue_ResourceId.Builder.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
public abstract class ResourceId {
  private static final String RESOURCE_ID_MAP_KEY = "ResourceId";

  @JsonProperty("id")
  public abstract UUID id();

  public static ResourceId create(UUID id) {
    return new AutoValue_ResourceId.Builder().id(id).build();
  }

  @Override
  public String toString() {
    return id().toString();
  }

  /** Retrieve and construct a ResourceId form {@link FlightMap}. */
  public static ResourceId retrieve(FlightMap map) {
    return ResourceId.create(map.get(RESOURCE_ID_MAP_KEY, UUID.class));
  }

  /** Stores ResourceId value in {@link FlightMap}. */
  public FlightMap store(FlightMap map) {
    map.put(RESOURCE_ID_MAP_KEY, id());
    return map;
  }

  /** Builder for {@link ResourceId}. */
  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder id(UUID value);

    public abstract ResourceId build();
  }
}
