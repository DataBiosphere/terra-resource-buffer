package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;

import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRuleFixedInterval;
import org.springframework.context.ApplicationContext;

/** {@link Flight} to create GCP project. */
public class GoogleProjectCreationFlight extends Flight {
  public GoogleProjectCreationFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    BufferDao bufferDao = ((ApplicationContext) applicationContext).getBean(BufferDao.class);
    CloudResourceManagerCow rmCow =
        ((ApplicationContext) applicationContext).getBean(CloudResourceManagerCow.class);
    CloudBillingClientCow billingCow =
        ((ApplicationContext) applicationContext).getBean(CloudBillingClientCow.class);
    ServiceUsageCow serviceUsageCow =
        ((ApplicationContext) applicationContext).getBean(ServiceUsageCow.class);
    CloudComputeCow cloudComputeCow =
        ((ApplicationContext) applicationContext).getBean(CloudComputeCow.class);
    DnsCow dnsCow = ((ApplicationContext) applicationContext).getBean(DnsCow.class);
    ClientConfig clientConfig =
        ((ApplicationContext) applicationContext).getBean(ClientConfig.class);
    GcpProjectConfig gcpProjectConfig =
        inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();
    GcpProjectIdGenerator idGenerator =
        ((ApplicationContext) applicationContext).getBean(GcpProjectIdGenerator.class);
    RetryRuleFixedInterval retryRule =
        new RetryRuleFixedInterval(/* intervalSeconds =*/ 180, /* maxCount =*/ 5);
    addStep(new GenerateResourceIdStep());
    addStep(new CreateResourceDbEntityStep(bufferDao));
    addStep(new GenerateProjectIdStep(gcpProjectConfig, idGenerator));
    addStep(new CreateProjectStep(rmCow, gcpProjectConfig), retryRule);
    addStep(new SetBillingInfoStep(billingCow, gcpProjectConfig));
    addStep(new EnableServicesStep(serviceUsageCow, gcpProjectConfig));
    addStep(new SetIamPolicyStep(rmCow, gcpProjectConfig));
    addStep(new CreateStorageLogBucketStep(clientConfig, gcpProjectConfig));
    addStep(new CreateNetworkStep(cloudComputeCow, gcpProjectConfig));
    addStep(new CreateRouteStep(cloudComputeCow, gcpProjectConfig));
    addStep(new CreateFirewallRuleStep(cloudComputeCow));
    addStep(new CreateSubnetsStep(cloudComputeCow, gcpProjectConfig));
    addStep(new CreateDnsZoneStep(cloudComputeCow, dnsCow, gcpProjectConfig));
    addStep(new CreateResourceRecordSetStep(dnsCow, gcpProjectConfig));
    addStep(new FinishResourceCreationStep(bufferDao));
  }
}
