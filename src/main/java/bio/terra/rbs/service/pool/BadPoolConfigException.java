package bio.terra.rbs.service.pool;

/** Exception thrown when pool configuration is invalid. */
public class BadPoolConfigException extends RuntimeException {
  public BadPoolConfigException(String message) {
    super(message);
  }

  public BadPoolConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
