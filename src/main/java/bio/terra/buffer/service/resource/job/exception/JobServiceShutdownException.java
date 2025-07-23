package bio.terra.buffer.service.resource.job.exception;

import bio.terra.common.exception.ServiceUnavailableException;

public class JobServiceShutdownException extends ServiceUnavailableException {
  public JobServiceShutdownException(String message, Throwable cause) {
    super(message, cause);
  }
}
