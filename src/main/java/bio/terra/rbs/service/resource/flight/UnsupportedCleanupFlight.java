package bio.terra.rbs.service.resource.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;

/**
 * A Flight for cleanups of resource types that are not yet supported. Always results in failure by
 * failing to cleanup the resource.
 */
public class UnsupportedCleanupFlight extends Flight {
  public UnsupportedCleanupFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    addStep(new UnsupportedCleanupStep());
  }
}
