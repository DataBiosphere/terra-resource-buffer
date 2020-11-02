package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.db.RbsDao;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** {@link Flight} to delete GCP project. */
public class GoogleProjectDeletionFlight extends Flight {
  public GoogleProjectDeletionFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);
    RbsDao rbsDao = ((ApplicationContext) applicationContext).getBean(RbsDao.class);
    CloudResourceManagerCow rmCow =
        ((ApplicationContext) applicationContext).getBean(CloudResourceManagerCow.class);
    addStep(new InitialResourceDeletionStep(rbsDao), retryRule);
    addStep(new DeleteProjectStep(rmCow), retryRule);
    addStep(new UpdateResourceAsDeletedStep(rbsDao), retryRule);
  }
}
