package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_READY;

import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;

/** Utilities used in Stairway steps. */
public class StepUtils {
  /**
   * The stairway retry rule for GCP API calls. Use longer wait time because cloud API quota,
   * outrage, and other unknown issues.
   */
  public static final RetryRuleFixedInterval CLOUD_API_DEFAULT_RETRY =
      new RetryRuleFixedInterval(/* intervalSeconds =*/ 90, /* maxCount =*/ 5);

  /**
   * The stairway retry rule for Buffer service internal operations. Use shorter wait time because
   * they all internal operations, e.g. DB write/read. And we are able to retry right away.
   */
  public static final RetryRuleFixedInterval INTERNAL_DEFAULT_RETRY =
      new RetryRuleFixedInterval(/* intervalSeconds =*/ 5, /* maxCount =*/ 5);

  /** Update resource state to READY and update working map's RESOURCE_READY boolean value. */
  public static void markResourceReady(BufferDao bufferDao, FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    bufferDao.updateResourceAsReady(
        ResourceId.retrieve(flightContext.getWorkingMap()),
        workingMap.get(FlightMapKeys.CLOUD_RESOURCE_UID, CloudResourceUid.class));
    workingMap.put(RESOURCE_READY, true);
  }
  /** Check resource is already marked as READY. This can prevent a READY resource got rollback. */
  public static boolean isResourceReady(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    return workingMap.get(RESOURCE_READY, Boolean.class) != null
        && workingMap.get(RESOURCE_READY, Boolean.class);
  }
}
