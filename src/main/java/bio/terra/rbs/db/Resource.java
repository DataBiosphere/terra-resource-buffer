package bio.terra.rbs.db;

import bio.terra.rbs.generated.model.CloudResourceUid;
import com.google.auto.value.AutoValue;
import java.time.Instant;
import javax.annotation.Nullable;

/** Represents a record in the resource table in the RBS database. */
@AutoValue
public abstract class Resource {
  public abstract ResourceId id();

  public abstract PoolId poolId();

  public abstract ResourceState state();

  @Nullable
  public abstract CloudResourceUid terraResourceUid();

  @Nullable
  public abstract RequestHandoutId requestHandoutId();

  @Nullable
  public abstract Instant creation();

  @Nullable
  public abstract Instant handoutTime();

  public static Builder builder() {
    return new AutoValue_Resource.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(ResourceId id);

    public abstract Builder poolId(PoolId poolId);

    public abstract Builder state(ResourceState state);

    public abstract Builder terraResourceUid(CloudResourceUid terraResourceUid);

    public abstract Builder requestHandoutId(RequestHandoutId requestHandoutId);

    public abstract Builder creation(Instant creation);

    public abstract Builder handoutTime(Instant handoutTime);

    public abstract Resource build();
  }
}
