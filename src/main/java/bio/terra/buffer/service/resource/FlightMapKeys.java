package bio.terra.buffer.service.resource;

import bio.terra.stairway.FlightMap;

/** Constant of stairway {@link FlightMap} keys in Resource Buffer Service. */
public class FlightMapKeys {
  public static final String CLOUD_RESOURCE_UID = "cloudResourceUid";
  public static final String GOOGLE_PROJECT_ID = "googleProjectId";
  public static final String GOOGLE_PROJECT_NUMBER = "googleProjectNumber";
  public static final String RESOURCE_CONFIG = "resourceConfig";

  /**
   * FlightMap key for a boolean value to indicate if resource creation completes and resource is
   * READY.
   */
  public static final String RESOURCE_READY = "resourceReady";

  public static final String NAT_CREATED_REGIONS = "natCreatedRegions";

  // parameters for all flight types
  public static final String DESCRIPTION = "description";
  public static final String STATUS_CODE = "status_code";
  public static final String RESPONSE = "response";
}
