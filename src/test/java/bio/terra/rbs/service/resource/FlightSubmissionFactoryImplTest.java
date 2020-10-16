package bio.terra.rbs.service.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.*;
import bio.terra.rbs.service.resource.flight.GoogleProjectCreationFlight;
import bio.terra.stairway.FlightMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FlightSubmissionFactoryImplTest {
  @Autowired FlightSubmissionFactoryImpl factory;

  @Test
  public void getCreationFlightClass() {
    Pool pool =
        Pool.builder().id(PoolId.create("id")).resourceType(ResourceType.GOOGLE_PROJECT).build();
    FlightMap flightMap = new FlightMap();
    pool.id().store(flightMap);
    assertEquals(
        FlightSubmissionFactory.FlightSubmission.create(
            GoogleProjectCreationFlight.class, flightMap),
        factory.getCreationFlightSubmission(pool));
  }

  @Test
  public void getDeletionFlightClass() {
    Resource resource = Resource.builder().id(ResourceId.create(UUID.randomUUID())).build();
    assertEquals(
        FlightSubmissionFactory.FlightSubmission.create(
            GoogleProjectCreationFlight.class, new FlightMap()),
        factory.getDeletionFlightSubmission(resource, ResourceType.GOOGLE_PROJECT));
  }
}
