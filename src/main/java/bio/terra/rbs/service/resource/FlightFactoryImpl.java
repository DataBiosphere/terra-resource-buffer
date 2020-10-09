package bio.terra.rbs.service.resource;

import bio.terra.rbs.db.ResourceType;
import bio.terra.stairway.Flight;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class FlightFactoryImpl implements FlightFactory {
    Map<ResourceType, Class<? extends Flight>> CREATION_FLIGHT_MAP = ImmutableMap.of(ResourceType.GOOGLE_PROJECT, )
    @Override
    public Class<? extends Flight> getCreationFlightClass(ResourceType type) {
        return null;
    }

    @Override
    public Class<? extends Flight> getDeletionFlightClass(ResourceType type) {
        return null;
    }
}
