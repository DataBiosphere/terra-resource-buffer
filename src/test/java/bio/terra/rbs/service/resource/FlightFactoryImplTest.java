package bio.terra.rbs.service.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.db.ResourceType;
import bio.terra.rbs.service.resource.flight.GoogleProjectCreationFlight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FlightFactoryImplTest {
  @Autowired FlightFactoryImpl factory;

  @Test
  public void getCreationFlightClass() {
    assertEquals(
        GoogleProjectCreationFlight.class,
        factory.getCreationFlightClass(ResourceType.GOOGLE_PROJECT));
  }

  @Test
  public void getDeletionFlightClass() {
    assertEquals(
        GoogleProjectCreationFlight.class,
        factory.getDeletionFlightClass(ResourceType.GOOGLE_PROJECT));
  }
}
