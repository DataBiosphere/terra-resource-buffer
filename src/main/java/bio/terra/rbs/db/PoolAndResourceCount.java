package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;

/** Represents a {@link Pool} with number of READY resources in the pool. */
@AutoValue
abstract class PoolAndResourceCount {
  abstract Pool pool();

  abstract int readyResourceCount();

  static PoolAndResourceCount create(Pool pool, int readyResourceCount) {
    return new AutoValue_PoolAndResourceCount(pool, readyResourceCount);
  }
}
