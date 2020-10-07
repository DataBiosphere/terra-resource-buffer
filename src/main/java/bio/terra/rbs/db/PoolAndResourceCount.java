package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;

/** Represents a {@link Pool} with number of activate resources in the pool. */
@AutoValue
abstract class PoolAndResourceCount {
  abstract Pool pool();

  abstract int resourceCount();

  static PoolAndResourceCount create(Pool pool, int resourceCount) {
    return new AutoValue_PoolAndResourceCount(pool, resourceCount);
  }
}
