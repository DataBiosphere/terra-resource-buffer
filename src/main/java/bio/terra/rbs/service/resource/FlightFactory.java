package bio.terra.rbs.service.resource;


import bio.terra.rbs.db.Resource;
import bio.terra.rbs.db.ResourceType;
import bio.terra.stairway.Flight;

/** An interface getting {@link Flight} from {@link ResourceType}. */
public interface FlightFactory {
    Class<? extends Flight> getCreationFlightClass(ResourceType type);
    Class<? extends Flight> getDeletionFlightClass(ResourceType type);
}
