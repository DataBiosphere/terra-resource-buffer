package bio.terra.rbs.db;

import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.auto.value.AutoValue;
import java.time.Instant;
import javax.annotation.Nullable;

/** Represents a record in the pool table in the RBS database. */
@AutoValue
public abstract class Pool {
  public abstract PoolId id();

  public abstract ResourceConfig resourceConfig();

  public abstract ResourceType resourceType();

  public abstract PoolStatus status();

  public abstract int size();

  public abstract Instant creation();

  @Nullable
  public abstract Instant expiration();

  public static Builder builder() {
    return new AutoValue_Pool.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(PoolId id);

    public abstract Builder resourceConfig(ResourceConfig resourceConfig);

    public abstract Builder resourceType(ResourceType resourceType);

    public abstract Builder status(PoolStatus status);

    public abstract Builder size(int size);

    public abstract Builder creation(Instant creation);

    public abstract Builder expiration(Instant expiration);

    public abstract Pool build();
  }
}
