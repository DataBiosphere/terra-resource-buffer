package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;

/** {@link Flight} to delete GCP project. */
public class GoogleProjectDeletionFlight extends Flight {
  public GoogleProjectDeletionFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }
}
