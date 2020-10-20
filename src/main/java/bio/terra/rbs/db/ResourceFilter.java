package bio.terra.rbs.db;

import bio.terra.rbs.common.PoolId;
import bio.terra.rbs.common.RequestHandoutId;
import bio.terra.rbs.common.ResourceState;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** A value class for filtering which resources are retrieved. */
@AutoValue
public abstract class ResourceFilter {
  /** If not empty, only resources with states within this set are allowed. */
  public abstract ImmutableSet<ResourceState> allowedStates();

  /** If not empty, only resources with states *not* within this set are allowed. */
  public abstract ImmutableSet<ResourceState> forbiddenStates();

  /** If present, only resources in the pool are allowed. */
  public abstract Optional<PoolId> poolId();

  /** If present, only resource with the requestHandoutId are allowed. */
  public abstract Optional<RequestHandoutId> requestHandoutId();

  /** If present, only return up to {@code limit} resources. */
  public abstract OptionalInt limit();

  /** Creates a new builder that allows all resources. */
  public static Builder builder() {
    return new AutoValue_ResourceFilter.Builder()
        .allowedStates(ImmutableSet.of())
        .forbiddenStates(ImmutableSet.of());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder allowedStates(Set<ResourceState> allowedStates);

    public abstract Builder forbiddenStates(Set<ResourceState> forbiddenStates);

    public abstract Builder poolId(PoolId poolId);

    public abstract Builder requestHandoutId(RequestHandoutId requestHandoutId);

    public abstract Builder limit(int value);

    abstract ResourceFilter autoBuild();

    public ResourceFilter build() {
      ResourceFilter filter = autoBuild();
      Preconditions.checkArgument(
          Sets.intersection(filter.allowedStates(), filter.forbiddenStates()).isEmpty(),
          String.format(
              "ResourceFilter contains states that are allowed and forbidden simultaneously: %s.",
              filter));
      return filter;
    }
  }
}
