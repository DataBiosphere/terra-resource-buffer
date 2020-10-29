package bio.terra.rbs.service.resource;

import bio.terra.rbs.common.Pool;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceType;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.StairwayExecutionException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Manages the Stairway flights to create or delete resources. */
@Component
public class FlightManager {
  private Logger logger = LoggerFactory.getLogger(FlightManager.class);

  private final FlightSubmissionFactory flightSubmissionFactory;
  private final Stairway stairway;

  @Autowired
  public FlightManager(
      FlightSubmissionFactory flightSubmissionFactory, StairwayComponent stairwayComponent) {
    this.flightSubmissionFactory = flightSubmissionFactory;
    this.stairway = stairwayComponent.get();
  }

  /** Submit Stairway Flight to create resource. */
  public Optional<String> submitCreationFlight(Pool pool) {
    return submitToStairway(flightSubmissionFactory.getCreationFlightSubmission(pool));
  }

  /** Submit Stairway Flight to delete resource. */
  public Optional<String> submitDeletionFlight(Resource resource, ResourceType resourceType) {
    return submitToStairway(
        flightSubmissionFactory.getDeletionFlightSubmission(resource, resourceType));
  }

  private Optional<String> submitToStairway(
      FlightSubmissionFactory.FlightSubmission flightSubmission) {
    String flightId = stairway.createFlightId();
    try {
      stairway.submitToQueue(
          flightId, flightSubmission.clazz(), flightSubmission.inputParameters());
      return Optional.of(flightId);
    } catch (DatabaseOperationException | StairwayExecutionException | InterruptedException e) {
      logger.error("Error submitting flight id: {}", flightId, e);
      return Optional.empty();
    }
  }
}
