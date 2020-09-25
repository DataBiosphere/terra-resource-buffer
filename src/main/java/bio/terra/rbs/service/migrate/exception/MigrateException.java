package bio.terra.rbs.service.migrate.exception;

import bio.terra.rbs.common.exception.InternalServerErrorException;

public class MigrateException extends InternalServerErrorException {
  public MigrateException(String message) {
    super(message);
  }

  public MigrateException(String message, Throwable cause) {
    super(message, cause);
  }
}
