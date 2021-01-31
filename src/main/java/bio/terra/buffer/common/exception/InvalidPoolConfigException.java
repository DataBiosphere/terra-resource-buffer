package bio.terra.buffer.common.exception;

import bio.terra.common.exception.InternalServerErrorException;

/** Exception thrown when pool configuration is invalid. */
public class InvalidPoolConfigException extends InternalServerErrorException {
  public InvalidPoolConfigException(String message) {
    super(message);
  }

  public InvalidPoolConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
