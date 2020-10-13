package bio.terra.rbs.service.resource;

import static bio.terra.rbs.service.resource.flight.CreateGoogleProjectStep.randomProjectId;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.db.Pool;
import bio.terra.rbs.db.Resource;
import bio.terra.rbs.db.ResourceType;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.StairwayExecutionException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
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
  private CloudResourceManagerCow rmCow;

  @Autowired
  public FlightManager(FlightFactory flightFactory, StairwayComponent stairwayComponent) {
    this.flightFactory = flightFactory;
    this.stairway = stairwayComponent.get();
    this.rmCow = rmCow;
  }

  public boolean submitCreationFlight(Pool pool) {
    String projectId = randomProjectId();
    Project project =
        new Project()
            .setProjectId(projectId)
            .setParent(new ResourceId().setType("folder").setId("637867149294"));
    FlightMap flightMap = new FlightMap();
    flightMap.put(FlightMapKeys.POOL_ID, pool.id());
    flightMap.put(FlightMapKeys.RESOURCE_CONFIG, pool.resourceConfig());
    return submitToStairway(flightFactory.getCreationFlightClass(pool.resourceType()), flightMap);
  }

  public boolean submitDeletionFlight(Resource resource, ResourceType resourceType) {
    // TODO: Add input into FlightMap
    return submitToStairway(flightFactory.getDeletionFlightClass(resourceType), new FlightMap());
  }

  private boolean submitToStairway(Class<? extends Flight> clazz, FlightMap flightMap) {
    String flightId = stairway.createFlightId();
    try {
      stairway.submitToQueue(flightId, clazz, flightMap);
      return true;
    } catch (DatabaseOperationException | StairwayExecutionException | InterruptedException e) {
      logger.error("Error submitting flight id: {}", flightId, e);
      return false;
    }
  }
}
