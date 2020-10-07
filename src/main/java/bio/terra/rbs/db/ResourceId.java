package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;
import java.util.UUID;

/** Wraps the id in db pool table. */
@AutoValue
public abstract class ResourceId {
  public abstract UUID id();

  public static ResourceId create(UUID id) {
    return new AutoValue_ResourceId(id);
  }

  @Override
  public String toString() {
    return id().toString();
  }
}
