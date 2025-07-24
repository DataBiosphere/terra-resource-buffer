package bio.terra.buffer.service.job.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class JobResponseException extends InternalServerErrorException {
  public JobResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
