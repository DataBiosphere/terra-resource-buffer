package bio.terra.buffer.common.exception;

/** API Exception caused by internal server error. */
public class ApiException extends InternalServerErrorException {
  public ApiException(String message) {
    super(message);
  }

  public ApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
