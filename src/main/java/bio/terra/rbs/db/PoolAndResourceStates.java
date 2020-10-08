package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMultiset;

/** Represents a {@link Pool} with number of resources by state. */
@AutoValue
public abstract class PoolAndResourceStates {
  public abstract Pool pool();

  public abstract ImmutableMultiset<ResourceState> resourceStates();

  public static Builder builder() {
    return new AutoValue_PoolAndResourceStates.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPool(Pool pool);

    public abstract ImmutableMultiset.Builder<ResourceState> resourceStatesBuilder();

    public abstract PoolAndResourceStates build();
  }
}
