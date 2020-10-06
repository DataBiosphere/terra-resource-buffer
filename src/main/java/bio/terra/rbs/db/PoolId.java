package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;

/** Wraps the id in db pool table. */
@AutoValue
public abstract class PoolId {
  public abstract String id();

  public static PoolId create(String id) {
    return new AutoValue_PoolId(id);
  }

  @Override
  public String toString() {
    return id();
  }
}
