package bio.terra.buffer.common;

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

    abstract ImmutableMultiset.Builder<ResourceState> resourceStatesBuilder();

    public Builder setResourceStateCount(ResourceState state, int count) {
      resourceStatesBuilder().setCount(state, count);
      return this;
    }

    public abstract PoolAndResourceStates build();
  }
}
