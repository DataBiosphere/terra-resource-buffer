package bio.terra.rbs.service.resource;

import bio.terra.stairway.FlightMap;

/** Constant of stairway {@link FlightMap} keys in RBS. */
public class FlightMapKeys {
  public static final String CLOUD_RESOURCE_UID = "cloudResourceUid";
  public static final String GOOGLE_PROJECT_ID = "googleProjectId";
  public static final String RESOURCE_CONFIG = "resourceConfig";

  /**
   * FlightMap key for a boolean value to indicate if resource creation completes and resource is
   * READY.
   */
  public static final String RESOURCE_READY = "resourceReady";
}
