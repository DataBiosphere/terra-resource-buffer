package bio.terra.buffer.service.resource.job.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class InvalidResultStateException extends InternalServerErrorException {
  public InvalidResultStateException(String message) {
    super(message);
  }
}
