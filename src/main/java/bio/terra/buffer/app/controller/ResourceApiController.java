package bio.terra.buffer.app.controller;

import bio.terra.buffer.common.Pool;
import bio.terra.buffer.generated.controller.ResourceApi;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.service.pool.PoolService;
import bio.terra.buffer.service.resource.FlightScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.Optional;

import static bio.terra.buffer.app.utils.ControllerUtils.jobToResponse;

@Controller
public class ResourceApiController implements ResourceApi {
    private final PoolService poolService;
    private final FlightScheduler flightScheduler;

    @Autowired
    ResourceApiController(PoolService poolService, FlightScheduler flightScheduler) {
        this.poolService = poolService;
        this.flightScheduler = flightScheduler;
    }

    @Override
    public ResponseEntity<JobModel> repairResource(String projectId) {
        GoogleProjectUid googleProjectUid = new GoogleProjectUid().projectId(projectId);
        Pool pool = poolService.getPoolForGoogleProject(googleProjectUid);
        Optional<String> flightId = flightScheduler.submitRepairResourceFlight(pool, googleProjectUid);
        return jobToResponse(flightScheduler.retrieveJob(flightId.get()));
    }
}
