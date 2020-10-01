package bio.terra.rbs.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;

import java.util.UUID;

/** Wraps the pool_id in db pool table. */
@AutoValue
@JsonSerialize(as = PoolId.class)
@JsonDeserialize(builder = AutoValue_PoolId.Builder.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
public abstract class PoolId {
  @JsonProperty("uuid")
  public abstract UUID uuid();

  public static PoolId create(UUID id) {
    return new AutoValue_PoolId.Builder().uuid(id).build();
  }

  @Override
  public String toString() {
    return uuid().toString();
  }

  /** Builder for {@link PoolId}. */
  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder uuid(UUID value);

    public abstract PoolId build();
  }
}
