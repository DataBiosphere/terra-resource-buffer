package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.app.configuration.BeanNames.GOOGLE_RM_COW;
import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_CONFIG;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** {@link Flight} to create GCP project. */
public class GoogleProjectCreationFlight extends Flight {
  public GoogleProjectCreationFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // TODO(PF-144): GCP VPC setup
    RbsDao rbsDao = ((ApplicationContext) applicationContext).getBean(RbsDao.class);
    CloudResourceManagerCow rmCow =
        ((ApplicationContext) applicationContext)
            .getBean(GOOGLE_RM_COW, CloudResourceManagerCow.class);
    GcpProjectConfig gcpProjectConfig =
        inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);
    addStep(new InitialCreateResourceStep(rbsDao));
    addStep(new CreateGoogleProjectStep(rmCow, gcpProjectConfig), retryRule);
    addStep(new FinalCreateResourceStep(rbsDao));
    // TODO(PF-144): GCP VPC setup
  }
}
