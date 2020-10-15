package bio.terra.rbs.service.resource;

import bio.terra.rbs.common.Pool;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceType;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
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

  private final FlightFactory flightFactory;
  private final Stairway stairway;

  @Autowired
  public FlightManager(FlightFactory flightFactory, StairwayComponent stairwayComponent) {
    this.flightFactory = flightFactory;
    this.stairway = stairwayComponent.get();
  }

  /** Submit Stairway Flight to create resource. */
  public Optional<String> submitCreationFlight(Pool pool) {
    FlightMap flightMap = new FlightMap();
    pool.id().store(flightMap);
    flightMap.put(FlightMapKeys.RESOURCE_CONFIG, pool.resourceConfig());
    return submitToStairway(flightFactory.getCreationFlightClass(pool.resourceType()), flightMap);
  }

  /** Submit Stairway Flight to delete resource. */
  public Optional<String> submitDeletionFlight(Resource resource, ResourceType resourceType) {
    // TODO: Add input into FlightMap
    return submitToStairway(flightFactory.getDeletionFlightClass(resourceType), new FlightMap());
  }

  private Optional<String> submitToStairway(Class<? extends Flight> clazz, FlightMap flightMap) {
    String flightId = stairway.createFlightId();
    try {
      stairway.submitToQueue(flightId, clazz, flightMap);
      return Optional.of(flightId);
    } catch (DatabaseOperationException | StairwayExecutionException | InterruptedException e) {
      logger.error("Error submitting flight id: {}", flightId, e);
      return Optional.empty();
    }
  }
}
