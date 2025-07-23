package bio.terra.buffer.service.resource;

import bio.terra.buffer.app.configuration.PrimaryConfiguration;
import bio.terra.buffer.common.MetricsHelper;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.PoolAndResourceStates;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.generated.model.JobModel;
import bio.terra.buffer.service.resource.job.exception.InvalidResultStateException;
import bio.terra.buffer.service.resource.job.exception.JobServiceShutdownException;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Periodically checks database state and submit Stairway flights to create/delete resource if
 * needed.
 */
@Component
public class FlightScheduler {

  private final Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final FlightManager flightManager;
  private final PrimaryConfiguration primaryConfiguration;
  private final StairwayComponent stairwayComponent;
  private final BufferDao bufferDao;
  private final MetricsHelper metricsHelper;

  @Autowired
  public FlightScheduler(
      FlightManager flightManager,
      PrimaryConfiguration primaryConfiguration,
      StairwayComponent stairwayComponent,
      BufferDao bufferDao,
      MetricsHelper metricsHelper) {
    this.flightManager = flightManager;
    this.primaryConfiguration = primaryConfiguration;
    this.stairwayComponent = stairwayComponent;
    this.bufferDao = bufferDao;
    this.metricsHelper = metricsHelper;
  }

  /**
   * Initialize the FlightScheduler, kicking off its tasks.
   *
   * <p>The StairwayComponent must be ready before calling this function.
   */
  public void initialize() {
    Preconditions.checkState(
        stairwayComponent.getStatus().equals(StairwayComponent.Status.OK),
        "Stairway must be ready before FlightScheduler can be initialized.");
    if (primaryConfiguration.isSchedulerEnabled()) {
      logger.info("Buffer scheduling enabled.");
    } else {
      // Do nothing if scheduling is disabled.
      logger.info("Buffer scheduling disabled.");
      return;
    }
    // The scheduled task will not execute concurrently with itself even if it takes a long time.
    // See javadoc on ScheduledExecutorService#scheduleAtFixedRate.
    executor.scheduleAtFixedRate(
        new LogThrowables(this::scheduleFlights),
        /* initialDelay= */ 0,
        /* period= */ primaryConfiguration.getFlightSubmissionPeriod().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Try to schedule flights to create and delete resources until resource count matches each pool
   * state or reach to configuration limit.
   */
  private void scheduleFlights() {
    logger.info("Beginning scheduling flights.");
    List<PoolAndResourceStates> poolAndResourceStatesList =
        bufferDao.retrievePoolAndResourceStates();
    for (PoolAndResourceStates poolAndResources : poolAndResourceStatesList) {
      metricsHelper.recordResourceStateCount(poolAndResources);
      if (poolAndResources.pool().status().equals(PoolStatus.ACTIVE)) {
        int poolSize = poolAndResources.pool().size();
        int readyAndCreatingCount =
            poolAndResources.resourceStates().count(ResourceState.CREATING)
                + poolAndResources.resourceStates().count(ResourceState.READY);
        logger.info(
            "Pool id: {}, size:{}, readyAndCreatingCount: {}.",
            poolAndResources.pool().id(),
            poolSize,
            readyAndCreatingCount);
        if (poolSize > readyAndCreatingCount) {
          int poolsToCreateCount = poolSize - readyAndCreatingCount;
          scheduleCreationFlights(poolAndResources.pool(), poolsToCreateCount);
        } else if (primaryConfiguration.isDeleteExcessResources()
            && poolAndResources.resourceStates().count(ResourceState.READY) > poolSize) {
          // Only delete READY resource, we hope future schedule runs will delete resources.
          // just turns to READY from CREATING.
          int excessReadyCount =
              poolAndResources.resourceStates().count(ResourceState.READY) - poolSize;
          scheduleDeletionFlights(poolAndResources.pool(), excessReadyCount);
        }
      } else {
        // Only deletion READY resource, we hope future schedule runs will deletion resources
        // just turns to READY from CREATING.
        scheduleDeletionFlights(
            poolAndResources.pool(), poolAndResources.resourceStates().count(ResourceState.READY));
      }
    }
  }

  /** Schedules up to {@code number} of resources creation flight for a pool. */
  private void scheduleCreationFlights(Pool pool, int number) {
    int flightToSchedule = Math.min(primaryConfiguration.getResourceCreationPerPoolLimit(), number);
    logger.info(
        "Beginning resource creation flights for pool: {}, target submission number: {} .",
        pool.id(),
        flightToSchedule);

    int successSubmitNum = 0;
    while (flightToSchedule-- > 0) {
      if (flightManager.submitCreationFlight(pool).isPresent()) {
        ++successSubmitNum;
      }
    }
    logger.info(
        "Successfully submitted {} number of resource creation flights for pool: {} .",
        successSubmitNum,
        pool.id());
  }

  /** Schedules up to {@code number} of resources deletion flight for a pool. */
  private void scheduleDeletionFlights(Pool pool, int number) {
    if (number == 0) {
      return;
    }
    int flightsToScheduleCount =
        Math.min(primaryConfiguration.getResourceDeletionPerPoolLimit(), number);
    logger.info(
        "Beginning resource deletion flights for pool: {}, target submission number: {} .",
        pool.id(),
        flightsToScheduleCount);

    // TODO: check pool expiration time before deleting projects
    List<Resource> resources =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, flightsToScheduleCount);
    int successSubmitNum = 0;
    for (Resource resource : resources) {
      boolean submissionSuccessful =
          flightManager.submitDeletionFlight(resource, pool.resourceType()).isPresent();
      if (submissionSuccessful) {
        ++successSubmitNum;
      }
    }
    logger.info(
        "Successfully submitted {} number of resource deletion flights for pool: {} .",
        successSubmitNum,
        pool.id());
  }

  public Optional<String> submitRepairResourceFlight(Pool pool, GoogleProjectUid projectUid) {
    return flightManager.submitRepairResourceFlight(pool, projectUid);
  }

  public JobModel retrieveJob(String jobId) {
    try {
      FlightState flightState = stairwayComponent.get().getFlightState(jobId);
      return mapFlightStateToJobModel(flightState);
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
//        if (getCompletionToFailureException(flightState).isPresent()) {
//          return JobModel.JobStatusEnum.FAILED;
//        }
        return JobModel.JobStatusEnum.SUCCEEDED;
    }
    return JobModel.JobStatusEnum.FAILED;
  }

  private FlightMap getResultMap(FlightState flightState) {
    FlightMap resultMap = flightState.getResultMap().orElse(null);
    if (resultMap == null) {
      throw new InvalidResultStateException("No result map returned from flight");
    }
    return resultMap;
  }

//  private Optional<Exception> getCompletionToFailureException(FlightState flightState) {
//    return flightState
//            .getResultMap()
//            .filter(rm -> rm.containsKey(CommonMapKeys.COMPLETION_TO_FAILURE_EXCEPTION))
//            .map(rm -> rm.get(CommonMapKeys.COMPLETION_TO_FAILURE_EXCEPTION, Exception.class));
//  }

  public void shutdown() {
    // Don't schedule  anything new during shutdown.
    executor.shutdown();
  }

  /**
   * Wraps a runnable to log any thrown errors to allow the runnable to still be run with a {@link
   * ScheduledExecutorService}.
   *
   * <p>ScheduledExecutorService scheduled tasks that throw errors stop executing.
   */
  private class LogThrowables implements Runnable {

    private final Runnable task;

    private LogThrowables(Runnable task) {
      this.task = task;
    }

    @Override
    public void run() {
      try {
        task.run();
      } catch (Throwable t) {
        logger.error(
            "Caught exception in FlightScheduler ScheduledExecutorService. StackTrace:\n"
                + t.getStackTrace(),
            t);
      }
    }
  }
}
