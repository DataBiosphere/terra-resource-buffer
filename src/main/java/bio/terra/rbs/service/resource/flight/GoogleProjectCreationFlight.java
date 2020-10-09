package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;

/** {@link Flight} to create GCP project. */
public class GoogleProjectCreationFlight extends Flight {
  public GoogleProjectCreationFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // TODO(PF-127): GCP Project Creation
    // TODO(PF-144): GCP VPC setup
  }
}
