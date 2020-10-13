package bio.terra.rbs.service.resource;

import bio.terra.rbs.db.ResourceType;
import bio.terra.stairway.Flight;
import org.springframework.stereotype.Component;

/** An interface getting {@link Flight} from {@link ResourceType}. */
@Component
public interface FlightFactory {
  Class<? extends Flight> getCreationFlightClass(ResourceType type);

  Class<? extends Flight> getDeletionFlightClass(ResourceType type);
}
