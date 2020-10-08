package bio.terra.rbs.db;

import com.google.auto.value.AutoValue;

/** Wraps the request_handout_id in db resource table. */
@AutoValue
public abstract class RequestHandoutId {
  public abstract String id();

  public static RequestHandoutId create(String id) {
    return new AutoValue_RequestHandoutId(id);
  }

  @Override
  public String toString() {
    return id();
  }
}
