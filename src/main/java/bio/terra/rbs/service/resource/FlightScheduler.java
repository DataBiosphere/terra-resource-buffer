package bio.terra.rbs.service.resource;

import bio.terra.rbs.app.configuration.PrimaryConfiguration;
import bio.terra.rbs.db.*;
import bio.terra.rbs.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Periodically checks database state and submit Stairway flights to create/delete resource if
 * needed.
 */
@Component
public class FlightScheduler {
  private final Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(4);

  private final FlightManager flightManager;
  private final PrimaryConfiguration primaryConfiguration;
  private final StairwayComponent stairwayComponent;
  private final RbsDao rbsDao;

  @Autowired
  public FlightScheduler(
      FlightManager flightManager,
      PrimaryConfiguration primaryConfiguration,
      StairwayComponent stairwayComponent,
      RbsDao rbsDao) {
    this.flightManager = flightManager;
    this.primaryConfiguration = primaryConfiguration;
    this.stairwayComponent = stairwayComponent;
    this.rbsDao = rbsDao;
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
      logger.info("Rbs scheduling enabled.");
    } else {
      // Do nothing if scheduling is disabled.
      logger.info("Rbs scheduling disabled.");
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
    List<PoolAndResourceStates> poolAndResourceStatesList = rbsDao.retrievePoolAndResourceStates();
    for (PoolAndResourceStates poolAndResources : poolAndResourceStatesList) {
      if (poolAndResources.pool().status().equals(PoolStatus.ACTIVE)) {
        int size = poolAndResources.pool().size();
        int readyAndCreatingCount =
            poolAndResources.resourceStates().count(ResourceState.CREATING)
                + poolAndResources.resourceStates().count(ResourceState.READY);
        if (size > readyAndCreatingCount) {
          scheduleCreationFlights(poolAndResources.pool(), size - readyAndCreatingCount);
        } else if (readyAndCreatingCount > size) {
          // Only deletion READY resource, we hope future schedule runs will deletion resources
          // just turns
          // to READY from CREATING.
          scheduleDeletionFlights(
              poolAndResources.pool(),
              poolAndResources.resourceStates().count(ResourceState.READY));
        }
      } else {
        // Only deletion READY resource, we hope future schedule runs will deletion resources
        // just turns
        // to READY from CREATING.
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

    while (flightToSchedule > 0) {
      flightManager.submitCreationFlight(pool);
      flightToSchedule--;
    }
  }

  /** Schedules up to {@code number} of resources creation flight for a pool. */
  private void scheduleDeletionFlights(Pool pool, int number) {
    int flightToSchedule = Math.min(primaryConfiguration.getResourceDeletionPerPoolLimit(), number);
    logger.info(
        "Beginning resource deletion flights for pool: {}, target submission number: {} .",
        pool.id(),
        flightToSchedule);

    List<Resource> resources = rbsDao.retrieveResources(ResourceState.READY, flightToSchedule);
    for (Resource resource : resources) {
      flightManager.submitDeleationFlight(resource);
    }
  }

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
