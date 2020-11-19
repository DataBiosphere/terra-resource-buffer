package bio.terra.buffer.common;

import com.google.auto.value.AutoValue;

/** Wraps the request_handout_id used in Resource Buffer Service. */
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
