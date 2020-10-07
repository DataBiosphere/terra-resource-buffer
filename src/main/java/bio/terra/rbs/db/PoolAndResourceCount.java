package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Multiset;

/** Represents a {@link Pool} with number of resources by state. */
@AutoValue
abstract class PoolAndResourceStates {
  abstract Pool pool();

  abstract Multiset<ResourceState> resourceStates();

  static PoolAndResourceStates create(Pool pool, Multiset<ResourceState> resourceStates) {
    return new AutoValue_PoolAndResourceStates(pool, resourceStates);
  }
}
