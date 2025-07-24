package bio.terra.buffer.service.resource;

import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.service.resource.flight.GoogleProjectCreationFlight;
import bio.terra.buffer.service.resource.flight.GoogleProjectDeletionFlight;
import bio.terra.buffer.service.resource.flight.GoogleProjectRepairFlight;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;

@Component
public class FlightSubmissionFactoryImpl implements FlightSubmissionFactory {
  /** Supported resource creation flight map. */
  private static final ImmutableMap<ResourceType, Class<? extends Flight>> CREATION_FLIGHT_MAP =
      ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectCreationFlight.class);

  /** Supported resource deletion flight map. */
  private static final ImmutableMap<ResourceType, Class<? extends Flight>> DELETION_FLIGHT_MAP =
      ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectDeletionFlight.class);

  /** Supported resource repair flight map. */
  private static final ImmutableMap<ResourceType, Class<? extends Flight>> REPAIR_FLIGHT_MAP =
          ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectRepairFlight.class);

  @Override
  public FlightSubmission getCreationFlightSubmission(Pool pool, ResourceId resourceId) {
    if (!CREATION_FLIGHT_MAP.containsKey(pool.resourceType())) {
      throw new UnsupportedOperationException(
          String.format(
              "Creation for ResourceType: %s is not supported, PoolId: %s", pool, pool.id()));
    }
    FlightMap flightMap = new FlightMap();
    pool.id().store(flightMap);
    resourceId.store(flightMap);
    flightMap.put(FlightMapKeys.RESOURCE_CONFIG, pool.resourceConfig());
    return FlightSubmission.create(CREATION_FLIGHT_MAP.get(pool.resourceType()), flightMap);
  }

  @Override
  public FlightSubmission getDeletionFlightSubmission(Resource resource, ResourceType type) {
    if (!DELETION_FLIGHT_MAP.containsKey(type)) {
      throw new UnsupportedOperationException(
          String.format("Deletion for ResourceType: %s is not supported", type.toString()));
    }
    FlightMap flightMap = new FlightMap();
    resource.id().store(flightMap);
    flightMap.put(FlightMapKeys.CLOUD_RESOURCE_UID, resource.cloudResourceUid());
    return FlightSubmission.create(DELETION_FLIGHT_MAP.get(type), flightMap);
  }

  @Override
  public FlightSubmission getRepairFlightSubmission(Pool pool, GoogleProjectUid projectUid) {
    if (!REPAIR_FLIGHT_MAP.containsKey(pool.resourceType())) {
      throw new UnsupportedOperationException(
              String.format(
                      "Repair operation for ResourceType: %s is not supported, PoolId: %s", pool, pool.id()));
    }
    FlightMap flightMap = new FlightMap();
    pool.id().store(flightMap);
    flightMap.put(FlightMapKeys.GOOGLE_PROJECT_ID, projectUid.getProjectId());
    flightMap.put(FlightMapKeys.RESOURCE_CONFIG, pool.resourceConfig());
    return FlightSubmission.create(REPAIR_FLIGHT_MAP.get(pool.resourceType()), flightMap);
  }

}
