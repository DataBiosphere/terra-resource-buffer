package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.flight.StepUtils.newCloudApiDefaultRetryRule;
import static bio.terra.buffer.service.resource.flight.StepUtils.newInternalDefaultRetryRule;

import bio.terra.buffer.db.BufferDao;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import org.springframework.context.ApplicationContext;

/** {@link Flight} to delete GCP project. */
public class GoogleProjectDeletionFlight extends Flight {

  public GoogleProjectDeletionFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    BufferDao bufferDao = ((ApplicationContext) applicationContext).getBean(BufferDao.class);
    CloudResourceManagerCow rmCow =
        ((ApplicationContext) applicationContext).getBean(CloudResourceManagerCow.class);
    addStep(new AssertResourceDeletingStep(bufferDao), newInternalDefaultRetryRule());
    addStep(new DeleteProjectStep(rmCow), newCloudApiDefaultRetryRule());
    addStep(new UpdateResourceAsDeletedStep(bufferDao), newInternalDefaultRetryRule());
  }
}
