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
import bio.terra.stairway.exception.StairwayException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JobService {
    private final FlightScheduler flightScheduler;

    @Autowired
    public JobService(FlightScheduler flightScheduler) {
        this.flightScheduler = flightScheduler;
    }

    public JobModel retrieveJob(String jobId) {
        try {
            FlightState flightState = flightScheduler.getStairway().getFlightState(jobId);
            return mapFlightStateToJobModel(flightState);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new JobServiceShutdownException("Job service interrupted", ex);
        }
    }

    /**
     * There are four cases to handle here:
     *
     * <ol>
     *   <li>Flight is still running. Return a 202 (Accepted) response
     *   <li>Successful flight: extract the resultMap RESPONSE as the target class. If a
     *       statusContainer is present, we try to retrieve the STATUS_CODE from the resultMap and
     *       store it in the container. That allows flight steps used in async REST API endpoints to
     *       set alternate success status codes. The status code defaults to OK, if it is not set in
     *       the resultMap.
     *   <li>Failed flight: if there is an exception, throw it. Note that we can only throw
     *       RuntimeExceptions to be handled by the global exception handler. Non-runtime exceptions
     *       require throw clauses on the controller methods; those are not present in the
     *       swagger-generated code, so it introduces a mismatch. Instead, in this code if the caught
     *       exception is not a runtime exception, then we throw JobResponseException passing in the
     *       Throwable to the exception. In the global exception handler, we retrieve the Throwable
     *       and use the error text from that in the error model
     *   <li>Failed flight: no exception present. We throw InvalidResultState exception
     * </ol>
     *
     * @param jobId to process
     * @return object of the result class pulled from the result map
     */
    public <T> JobService.JobResultWithStatus<T> retrieveJobResult(
            String jobId, Class<T> resultClass) {
        try {
            return retrieveJobResultWorker(jobId, resultClass);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new JobServiceShutdownException("Job service interrupted", ex);
        }
    }

    public JobModel mapFlightStateToJobModel(FlightState flightState) {
        FlightMap inputParameters = flightState.getInputParameters();
        String description = inputParameters.get(FlightMapKeys.DESCRIPTION, String.class);
        String submittedDate = flightState.getSubmitted().toString();
        JobModel.JobStatusEnum jobStatus = getJobStatus(flightState);

        String completedDate = null;
        HttpStatus statusCode = HttpStatus.ACCEPTED;

        if (flightState.getCompleted().isPresent()) {
            FlightMap resultMap = getResultMap(flightState);
            // The STATUS_CODE return only needs to be used to return alternate success responses.
            // If it is not present, then we set it to the default OK status.
            statusCode = resultMap.get(FlightMapKeys.STATUS_CODE, HttpStatus.class);
            if (statusCode == null) {
                statusCode = HttpStatus.OK;
            }

            completedDate = flightState.getCompleted().get().toString();
        }

        return new JobModel()
                .id(flightState.getFlightId())
                .className(flightState.getClassName())
                .description(description)
                .jobStatus(jobStatus)
                .statusCode(statusCode.value())
                .submitted(submittedDate)
                .completed(completedDate);
    }

    private JobModel.JobStatusEnum getJobStatus(FlightState flightState) {
        FlightStatus flightStatus = flightState.getFlightStatus();
        switch (flightStatus) {
            case ERROR:
            case FATAL:
                return JobModel.JobStatusEnum.FAILED;
            case RUNNING:
                return JobModel.JobStatusEnum.RUNNING;
            case SUCCESS:
                return JobModel.JobStatusEnum.SUCCEEDED;
        }
        return JobModel.JobStatusEnum.FAILED;
    }

    private <T> JobService.JobResultWithStatus<T> retrieveJobResultWorker(String jobId, Class<T> resultClass)
            throws StairwayException, InterruptedException {

        FlightState flightState = flightScheduler.getStairway().getFlightState(jobId);

        JobModel.JobStatusEnum jobStatus = getJobStatus(flightState);

        switch (jobStatus) {
            case FAILED:
                final Exception exceptionToThrow;

                if (flightState.getException().isPresent()) {
                    exceptionToThrow = flightState.getException().get();
                } else {
                    exceptionToThrow =
                            new InvalidResultStateException("Failed operation with no exception reported");
                }
                if (exceptionToThrow instanceof RuntimeException) {
                    throw (RuntimeException) exceptionToThrow;
                } else {
                    throw new JobResponseException("wrap non-runtime exception", exceptionToThrow);
                }
            case SUCCEEDED:
                FlightMap resultMap = flightState.getResultMap().orElse(null);
                if (resultMap == null) {
                    throw new InvalidResultStateException("No result map returned from flight");
                }
                HttpStatus statusCode =
                        resultMap.get(FlightMapKeys.STATUS_CODE, HttpStatus.class);
                if (statusCode == null) {
                    statusCode = HttpStatus.OK;
                }
                return new JobService.JobResultWithStatus<T>()
                        .statusCode(statusCode)
                        .result(resultMap.get(FlightMapKeys.RESPONSE, resultClass));

            case RUNNING:
                return new JobService.JobResultWithStatus<T>().statusCode(HttpStatus.ACCEPTED);

            default:
                throw new InvalidResultStateException("Impossible case reached");
        }
    }


    private FlightMap getResultMap(FlightState flightState) {
        FlightMap resultMap = flightState.getResultMap().orElse(null);
        if (resultMap == null) {
            throw new InvalidResultStateException("No result map returned from flight");
        }
        return resultMap;
    }

    public static class JobResultWithStatus<T> {
        private T result;
        private HttpStatus statusCode;

        public T getResult() {
            return result;
        }

        public JobResultWithStatus<T> result(T result) {
            this.result = result;
            return this;
        }

        public HttpStatus getStatusCode() {
            return statusCode;
        }

        public JobResultWithStatus<T> statusCode(HttpStatus httpStatus) {
            this.statusCode = httpStatus;
            return this;
        }
    }


}
