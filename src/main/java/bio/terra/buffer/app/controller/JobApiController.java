package bio.terra.buffer.app.controller;

import bio.terra.buffer.common.SqlSortDirection;
import bio.terra.buffer.generated.controller.JobsApi;
import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.generated.model.SqlSortDirectionDescDefault;
import bio.terra.buffer.service.job.JobService;
import bio.terra.common.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

import static bio.terra.buffer.app.utils.ControllerUtils.jobToResponse;

@Controller
public class JobApiController implements JobsApi {
    private final JobService jobService;

    @Autowired
    JobApiController(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public ResponseEntity<List<JobModel>> enumerateJobs(
            Integer offset, Integer limit, SqlSortDirectionDescDefault direction, String className) {
        validateOffsetAndLimit(offset, limit);
        List<JobModel> results =
                jobService.enumerateJobs(
                        offset, limit, SqlSortDirection.from(direction), className);
        return new ResponseEntity<>(results, HttpStatus.OK);
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

    private void validateOffsetAndLimit(Integer offset, Integer limit) {
        List<String> errors = new ArrayList<>();
        offset = (offset == null) ? 0 : offset;
        if (offset < 0) {
            errors.add("Offset must be greater than or equal to 0.");
        }

        limit = (limit == null) ? 10 : limit;
        if (limit < 1) {
            errors.add("Limit must be greater than or equal to 1.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(" ", errors));
        }
    }
}
