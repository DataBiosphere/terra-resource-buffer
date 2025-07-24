package bio.terra.buffer.app.utils;

import bio.terra.buffer.generated.model.JobModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ControllerUtils {

  private ControllerUtils() {}


  public static ResponseEntity<JobModel> jobToResponse(JobModel job) {
    if (job.getJobStatus() == JobModel.JobStatusEnum.RUNNING) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .header("Location", String.format("/api/jobs/v1/%s", job.getId()))
          .body(job);
    } else {
      return ResponseEntity.status(HttpStatus.OK)
          .header("Location", String.format("/api/jobs/v1/%s/result", job.getId()))
          .body(job);
    }
  }
}
