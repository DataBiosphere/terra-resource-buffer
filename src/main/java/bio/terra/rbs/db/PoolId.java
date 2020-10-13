package bio.terra.rbs.db;

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
  public abstract String id();

  public static PoolId create(String id) {
    return new AutoValue_PoolId.Builder().id(id).build();
  }

  @Override
  public String toString() {
    return id();
  }

  /** Builder for {@link PoolId}. */
  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder id(String value);

    public abstract PoolId build();
  }
}
