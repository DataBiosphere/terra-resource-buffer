package bio.terra.buffer.app.controller;

import bio.terra.buffer.common.exception.ErrorReportException;
import bio.terra.buffer.generated.model.ErrorReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

/**
 * This module provides a top-level exception handler for controllers. All exceptions that rise
 * through the controllers are caught in this handler. It converts the exceptions into standard
 * ErrorReport responses.
 *
 * <p>TODO: This class and other exception classes are exactly the same as Workspace Manager and
 * Data Repo's code. Use the common library once we have.
 */
abstract class AbstractGlobalExceptionHandler <T>{
  private final Logger logger = LoggerFactory.getLogger(AbstractGlobalExceptionHandler.class);

  // -- Error Report - one of our exceptions --
  @ExceptionHandler(ErrorReportException.class)
  public ResponseEntity<T> errorReportHandler(ErrorReportException ex) {
    return buildErrorReport(ex, ex.getStatusCode(), ex.getCauses());
  }

  // -- validation exceptions - we don't control the exception raised
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    NoHandlerFoundException.class
  })
  public ResponseEntity<T> validationExceptionHandler(Exception ex) {
    return buildErrorReport(ex, HttpStatus.BAD_REQUEST, null);
  }

  // -- catchall - log so we can understand what we have missed in the handlers above
  @ExceptionHandler(Exception.class)
  public ResponseEntity<T> catchallHandler(Exception ex) {
    logger.error("Exception caught by catchall hander", ex);
    return buildErrorReport(ex, HttpStatus.INTERNAL_SERVER_ERROR, null);
  }

  private ResponseEntity<T> buildErrorReport(
      Throwable ex, HttpStatus statusCode, List<String> causes) {
    StringBuilder combinedCauseString = new StringBuilder();
    logger.error("Global exception handler: catch stack", ex);
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      combinedCauseString.append("cause: " + cause.toString() + ", ");
    }
    logger.error("Global exception handler: " + combinedCauseString.toString(), ex);

    return new ResponseEntity<>(generateErrorReport(ex, statusCode, causes), statusCode);
  }

  abstract T generateErrorReport(Throwable ex, HttpStatus statusCode, List<String> causes);
}
