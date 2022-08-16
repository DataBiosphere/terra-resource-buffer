package bio.terra.buffer.app.controller;

import bio.terra.buffer.generated.model.ErrorReport;
import bio.terra.common.exception.ErrorReportException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * This module provides a top-level exception handler for controllers. All exceptions that rise
 * through the controllers are caught in this handler. It converts the exceptions into standard
 * ErrorReport responses.
 *
 * // TODO: move to terra-common-lib PF-1912
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // -- Error Report - one of our exceptions --
  @ExceptionHandler(ErrorReportException.class)
  public ResponseEntity<ErrorReport> errorReportHandler(ErrorReportException ex) {
    return buildErrorReport(ex, ex.getStatusCode(), ex.getCauses());
  }

  // -- validation exceptions - we don't control the exception raised
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    NoHandlerFoundException.class
  })
  public ResponseEntity<ErrorReport> validationExceptionHandler(Exception ex) {
    return buildErrorReport(ex, HttpStatus.BAD_REQUEST, null);
  }

  // -- catchall - log so we can understand what we have missed in the handlers above
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorReport> catchallHandler(Exception ex) {
    logger.error("Exception caught by catch-all handler", ex);
    return buildErrorReport(ex, HttpStatus.INTERNAL_SERVER_ERROR, null);
  }

  private ResponseEntity<ErrorReport> buildErrorReport(
      Throwable ex, HttpStatus statusCode, List<String> causes) {
    logger.error("Global exception handler: catch stack", ex);
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      logger.error("   cause: " + cause.toString());
    }
    ErrorReport errorReport =
        new ErrorReport().message(ex.getMessage()).statusCode(statusCode.value()).causes(causes);
    return new ResponseEntity<>(errorReport, statusCode);
  }
}
