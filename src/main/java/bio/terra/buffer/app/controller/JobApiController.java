package bio.terra.buffer.app.controller;

import bio.terra.buffer.generated.controller.JobsApi;
import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.service.job.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import static bio.terra.buffer.app.utils.ControllerUtils.jobToResponse;

@Controller
public class JobApiController implements JobsApi {
    private final JobService jobService;

    @Autowired
    JobApiController(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public ResponseEntity<JobModel> retrieveJob(String id) {
        JobModel job = jobService.retrieveJob(id);
        return jobToResponse(job);
    }

    @Override
    public ResponseEntity<Object> retrieveJobResult(String id) {
        JobService.JobResultWithStatus<Object> resultHolder =
                jobService.retrieveJobResult(id, Object.class);
        return ResponseEntity.status(resultHolder.getStatusCode()).body(resultHolder.getResult());
    }
}
