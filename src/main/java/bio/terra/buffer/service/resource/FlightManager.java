package bio.terra.buffer.service.resource;

import bio.terra.buffer.common.*;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.service.stairway.StairwayComponent;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.StairwayExecutionException;
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
      StairwayComponent stairwayComponent,
      TransactionTemplate transactionTemplate) {
    this.bufferDao = bufferDao;
    this.flightSubmissionFactory = flightSubmissionFactory;
    this.stairway = stairwayComponent.get();
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
   * <p>This should be done as a part of a transaction because we don't want resource state update
   * without submitting a flight. The TransactionStatus is unused, but a part of the signature as a
   * reminder.
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
   * <p>This should be done as a part of a transaction because we don't want resource state update
   * without submitting a flight. The TransactionStatus is unused, but a part of the signature as a
   * reminder.
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
    } catch (DatabaseOperationException | StairwayExecutionException | InterruptedException e) {
      logger.error("Error submitting flight id: {}", flightId, e);
      // Let TranscationTemplate rollback the previous DB commit if failed to submit a flight. Here we try-catch
      // checked exception and we need to manually set it up.
      status.setRollbackOnly();
      return Optional.empty();
    }
  }
}
