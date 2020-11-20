package bio.terra.buffer.common.exception;

/** This exception maps to HttpStatus.INTERNAL_SERVER_ERROR in the GlobalExceptionHandler. */
public class InternalServerErrorException extends ErrorReportException {
  public InternalServerErrorException(String message) {
    super(message);
  }

  public InternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
