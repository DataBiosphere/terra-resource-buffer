package bio.terra.buffer.app.controller;

import bio.terra.buffer.generated.controller.JobsApi;
import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.service.resource.FlightScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import static bio.terra.buffer.app.utils.ControllerUtils.jobToResponse;

@Controller
public class JobApiController implements JobsApi {
    private final FlightScheduler flightScheduler;

    @Autowired
    JobApiController(FlightScheduler flightSchedule) {
        this.flightScheduler = flightSchedule;
    }

    @Override
    public ResponseEntity<JobModel> retrieveJob(String id) {
        JobModel job = flightScheduler.retrieveJob(id);
        return jobToResponse(job);
    }

    @Override
    public ResponseEntity<Object> retrieveJobResult(String id) {
        FlightScheduler.JobResultWithStatus<Object> resultHolder =
                flightScheduler.retrieveJobResult(id, Object.class);
        return ResponseEntity.status(resultHolder.getStatusCode()).body(resultHolder.getResult());
    }
}
