package bio.terra.buffer.service.job;

import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.service.job.exception.InvalidResultStateException;
import bio.terra.buffer.service.job.exception.JobResponseException;
import bio.terra.buffer.service.job.exception.JobServiceShutdownException;
import bio.terra.buffer.service.resource.FlightMapKeys;
import bio.terra.buffer.service.resource.FlightScheduler;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobServiceTest {
    private FlightScheduler flightScheduler;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        flightScheduler = mock(FlightScheduler.class);
        when(flightScheduler.getStairway()).thenReturn(Mockito.mock(bio.terra.stairway.Stairway.class));
        jobService = new JobService(flightScheduler);
    }

    @Test
    void testRetrieveJobSuccess() throws Exception {
        FlightState flightState = mock(FlightState.class);
        when(flightScheduler.getStairway().getFlightState("jobId")).thenReturn(flightState);

        when(flightState.getInputParameters()).thenReturn(new FlightMap());
        when(flightState.getSubmitted()).thenReturn(Instant.now());
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.RUNNING);
        when(flightState.getFlightId()).thenReturn("jobId");
        when(flightState.getClassName()).thenReturn("TestClass");
        when(flightState.getCompleted()).thenReturn(Optional.empty());

        JobModel jobModel = jobService.retrieveJob("jobId");
        assertEquals("jobId", jobModel.getId());
        assertEquals(JobModel.JobStatusEnum.RUNNING, jobModel.getJobStatus());
    }

    @Test
    void testRetrieveJobInterruptedException() throws Exception {
        when(flightScheduler.getStairway().getFlightState("jobId"))
                .thenThrow(new InterruptedException("Error retrieving job"));
        assertThrows(JobServiceShutdownException.class, () -> jobService.retrieveJob("jobId"));
    }

    @Test
    void testMapFlightStateToJobModelCompleted() {
        FlightState flightState = mock(FlightState.class);
        FlightMap inputMap = new FlightMap();
        inputMap.put(FlightMapKeys.DESCRIPTION, "desc");
        when(flightState.getInputParameters()).thenReturn(inputMap);
        when(flightState.getSubmitted()).thenReturn(Instant.now());
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.SUCCESS);
        when(flightState.getFlightId()).thenReturn("jobId");
        when(flightState.getClassName()).thenReturn("TestClass");
        when(flightState.getCompleted()).thenReturn(Optional.of(Instant.now()));

        FlightMap resultMap = new FlightMap();
        resultMap.put(FlightMapKeys.STATUS_CODE, HttpStatus.OK);
        when(flightState.getResultMap()).thenReturn(Optional.of(resultMap));

        JobModel jobModel = jobService.mapFlightStateToJobModel(flightState);
        assertEquals(HttpStatus.OK.value(), jobModel.getStatusCode());
        assertNotNull(jobModel.getCompleted());
    }

    @Test
    void testRetrieveJobResultRunning() throws Exception {
        FlightState flightState = mock(FlightState.class);
        when(flightScheduler.getStairway().getFlightState("jobId")).thenReturn(flightState);
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.RUNNING);

        JobService.JobResultWithStatus<String> result = jobService.retrieveJobResult("jobId", String.class);
        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertNull(result.getResult());
    }

    @Test
    void testRetrieveJobResultSucceeded() throws Exception {
        FlightState flightState = mock(FlightState.class);
        when(flightScheduler.getStairway().getFlightState("jobId")).thenReturn(flightState);
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.SUCCESS);

        FlightMap resultMap = new FlightMap();
        resultMap.put(FlightMapKeys.STATUS_CODE, HttpStatus.OK);
        resultMap.put(FlightMapKeys.RESPONSE, "success");
        when(flightState.getResultMap()).thenReturn(Optional.of(resultMap));

        JobService.JobResultWithStatus<String> result = jobService.retrieveJobResult("jobId", String.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("success", result.getResult());
    }

    @Test
    void testRetrieveJobResultFailedWithRuntimeException() throws Exception {
        FlightState flightState = mock(FlightState.class);
        when(flightScheduler.getStairway().getFlightState("jobId")).thenReturn(flightState);
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.ERROR);
        when(flightState.getException()).thenReturn(Optional.of(new RuntimeException("fail")));

        assertThrows(RuntimeException.class, () -> jobService.retrieveJobResult("jobId", String.class));
    }

    @Test
    void testRetrieveJobResultFailedWithException() throws Exception {
        FlightState flightState = mock(FlightState.class);
        when(flightScheduler.getStairway().getFlightState("jobId")).thenReturn(flightState);
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.ERROR);
        when(flightState.getException()).thenReturn(Optional.of(new Exception("fail")));

        assertThrows(JobResponseException.class, () -> jobService.retrieveJobResult("jobId", String.class));
    }

    @Test
    void testRetrieveJobResultFailedNoException() throws Exception {
        FlightState flightState = mock(FlightState.class);
        when(flightScheduler.getStairway().getFlightState("jobId")).thenReturn(flightState);
        when(flightState.getFlightStatus()).thenReturn(FlightStatus.ERROR);
        when(flightState.getException()).thenReturn(Optional.empty());

        assertThrows(InvalidResultStateException.class, () -> jobService.retrieveJobResult("jobId", String.class));
    }
}