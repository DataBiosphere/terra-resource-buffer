package bio.terra.rbs.common.exception;

/** This exception maps to HttpStatus.NOT_FOUND in the GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
