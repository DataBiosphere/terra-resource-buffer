package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_CONFIG;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
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
    RbsDao rbsDao = ((ApplicationContext) applicationContext).getBean(RbsDao.class);
    CloudResourceManagerCow rmCow =
        ((ApplicationContext) applicationContext).getBean(CloudResourceManagerCow.class);
    CloudBillingClientCow billingCow =
        ((ApplicationContext) applicationContext).getBean(CloudBillingClientCow.class);
    ServiceUsageCow serviceUsageCow =
        ((ApplicationContext) applicationContext).getBean(ServiceUsageCow.class);
    CloudComputeCow cloudComputeCow =
        ((ApplicationContext) applicationContext).getBean(CloudComputeCow.class);
    DnsCow dnsCow = ((ApplicationContext) applicationContext).getBean(DnsCow.class);
    GcpProjectConfig gcpProjectConfig =
        inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);
    addStep(new GenerateResourceIdStep());
    addStep(new CreateResourceDbEntityStep(rbsDao));
    addStep(new GenerateProjectIdStep());
    addStep(new CreateProjectStep(rmCow, gcpProjectConfig), retryRule);
    addStep(new SetBillingInfoStep(billingCow, gcpProjectConfig));
    addStep(new EnableServicesStep(serviceUsageCow, gcpProjectConfig));
    addStep(new SetIamPolicyStep(rmCow, gcpProjectConfig));
    addStep(new CreateNetworkStep(cloudComputeCow, gcpProjectConfig));
    addStep(new CreateRouteStep(cloudComputeCow, gcpProjectConfig));
    addStep(new CreateSubnetsStep(cloudComputeCow, gcpProjectConfig));
    addStep(new CreateDnsZoneStep(cloudComputeCow, dnsCow, gcpProjectConfig));
    addStep(new CreateResourceRecordSetStep(dnsCow, gcpProjectConfig));
    addStep(new FinishResourceCreationStep(rbsDao));
  }
}
