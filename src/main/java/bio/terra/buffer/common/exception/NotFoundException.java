package bio.terra.buffer.common.exception;

import org.springframework.http.HttpStatus;

/** This exception maps to HttpStatus.NOT_FOUND in the GlobalExceptionHandler. */
public class NotFoundException extends ErrorReportException {
  private static final HttpStatus thisStatus = HttpStatus.NOT_FOUND;

  public NotFoundException(String message) {
    super(message, null, thisStatus);
  }

  public NotFoundException(String message, Throwable cause) {
    super(message, cause, null, thisStatus);
  }
}
