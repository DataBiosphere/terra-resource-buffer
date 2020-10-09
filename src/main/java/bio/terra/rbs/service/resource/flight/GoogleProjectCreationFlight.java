package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;

/** {@link Flight} to create GCP project. */
public class GoogleProjectCreationFlight extends Flight {
  public GoogleProjectCreationFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }
}
