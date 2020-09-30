package bio.terra.rbs.common.exception;

/** Exception thrown when pool configuration is invalid. */
public class InvalidPoolConfigException extends InternalServerErrorException{
    public InvalidPoolConfigException(String message) {
        super(message);
    }

    public InvalidPoolConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
