package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_READY;

import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;

/** Utilities used in Stairway steps. */
public class StepUtils {

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
