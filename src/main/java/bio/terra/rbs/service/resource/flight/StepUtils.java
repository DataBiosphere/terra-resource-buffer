package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_READY;

import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;

/** Utilities used in Stairway steps. */
public class StepUtils {

  /** Update resource state to READY and update working map's RESOURCE_READY boolean value. */
  public static void markResourceReady(RbsDao rbsDao, FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    rbsDao.updateResourceAsReady(
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
