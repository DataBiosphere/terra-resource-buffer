package bio.terra.buffer.service.resource;

import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.db.BufferDao;
import bio.terra.common.stairway.StairwayLifecycleManager;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.StairwayException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Manages the Stairway flights to create or delete resources. */
@Component
public class FlightManager {
  private Logger logger = LoggerFactory.getLogger(FlightManager.class);

  private final BufferDao bufferDao;
  private final FlightSubmissionFactory flightSubmissionFactory;
  private final Stairway stairway;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  public FlightManager(
      BufferDao bufferDao,
      FlightSubmissionFactory flightSubmissionFactory,
      StairwayLifecycleManager stairwayLifecycleManager,
      TransactionTemplate transactionTemplate) {
    this.bufferDao = bufferDao;
    this.flightSubmissionFactory = flightSubmissionFactory;
    this.stairway = stairwayLifecycleManager.get();
    this.transactionTemplate = transactionTemplate;
  }

  /** Submit Stairway Flight to create resource. */
  public Optional<String> submitCreationFlight(Pool pool) {
    return transactionTemplate.execute(status -> createResourceEntityAndSubmitFlight(pool, status));
  }

  /** Submit Stairway Flight to delete resource. */
  public Optional<String> submitDeletionFlight(Resource resource, ResourceType resourceType) {
    return transactionTemplate.execute(
        status -> updateResourceAsDeletingAndSubmitFlight(resource, resourceType, status));
  }

  /**
   * Create entity in resource table with CREATING and submit creation flight.
   *
   * <p>If the Stairway submission fails, the transaction will be rolled back. If the Stairway
   * submission succeeds but the DB update transaction fails, the flight checks the DB state and
   * aborts if the state is bad.
   */
  private Optional<String> createResourceEntityAndSubmitFlight(
      Pool pool, TransactionStatus status) {
    ResourceId resourceId = ResourceId.create(UUID.randomUUID());
    bufferDao.createResource(
        Resource.builder()
            .id(resourceId)
            .poolId(pool.id())
            .creation(Instant.now())
            .state(ResourceState.CREATING)
            .build());
    return submitToStairway(
        flightSubmissionFactory.getCreationFlightSubmission(pool, resourceId), status);
  }

  /**
   * Update a READY resource state to DELETING and submit deletion flight.
   *
   * <p>If the Stairway submission fails, the transaction will be rolled back. If the Stairway
   * submission succeeds but the DB update transaction fails, the flight checks the DB state and
   * aborts if the state is bad.
   */
  private Optional<String> updateResourceAsDeletingAndSubmitFlight(
      Resource resource, ResourceType resourceType, TransactionStatus status) {
    if (bufferDao.updateReadyResourceToDeleting(resource.id())) {
      return submitToStairway(
          flightSubmissionFactory.getDeletionFlightSubmission(resource, resourceType), status);
    }
    logger.info("Failed to submit resource deletion flight for resource{}", resource.id());
    return Optional.empty();
  }

  private Optional<String> submitToStairway(
      FlightSubmissionFactory.FlightSubmission flightSubmission, TransactionStatus status) {
    String flightId = stairway.createFlightId();
    try {
      stairway.submitToQueue(
          flightId, flightSubmission.clazz(), flightSubmission.inputParameters());
      return Optional.of(flightId);
    } catch (StairwayException | InterruptedException e) {
      logger.error("Error submitting flight id: {}", flightId, e);
      // If the flight submission fails, set the transaction to be rolled back.
      status.setRollbackOnly();
      return Optional.empty();
    }
  }
}
