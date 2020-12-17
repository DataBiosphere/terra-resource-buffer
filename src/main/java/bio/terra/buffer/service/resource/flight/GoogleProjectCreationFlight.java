package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.buffer.service.resource.flight.StepUtils.RETRY_RULE;

import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
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
    IamCow iamCow = ((ApplicationContext) applicationContext).getBean(IamCow.class);
    ClientConfig clientConfig =
        ((ApplicationContext) applicationContext).getBean(ClientConfig.class);
    GcpProjectConfig gcpProjectConfig =
        inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();
    GcpProjectIdGenerator idGenerator =
        ((ApplicationContext) applicationContext).getBean(GcpProjectIdGenerator.class);
    addStep(new GenerateResourceIdStep(), RETRY_RULE);
    addStep(new CreateResourceDbEntityStep(bufferDao), RETRY_RULE);
    addStep(new GenerateProjectIdStep(gcpProjectConfig, idGenerator), RETRY_RULE);
    addStep(new CreateProjectStep(rmCow, gcpProjectConfig), RETRY_RULE);
    addStep(new SetBillingInfoStep(billingCow, gcpProjectConfig), RETRY_RULE);
    addStep(new EnableServicesStep(serviceUsageCow, gcpProjectConfig), RETRY_RULE);
    addStep(new SetIamPolicyStep(rmCow, gcpProjectConfig), RETRY_RULE);
    addStep(new CreateStorageLogBucketStep(clientConfig, gcpProjectConfig), RETRY_RULE);
    addStep(new DeleteDefaultServiceAccountStep(iamCow), RETRY_RULE);
    addStep(new DeleteDefaultFirewallRulesStep(cloudComputeCow), RETRY_RULE);
    addStep(new DeleteDefaultNetworkStep(cloudComputeCow, gcpProjectConfig), RETRY_RULE);
    addStep(new CreateNetworkStep(cloudComputeCow, gcpProjectConfig), RETRY_RULE);
    addStep(new CreateRouteStep(cloudComputeCow, gcpProjectConfig), RETRY_RULE);
    addStep(new CreateFirewallRuleStep(cloudComputeCow), RETRY_RULE);
    addStep(new CreateSubnetsStep(cloudComputeCow, gcpProjectConfig), RETRY_RULE);
    addStep(new CreateDnsZoneStep(cloudComputeCow, dnsCow, gcpProjectConfig), RETRY_RULE);
    addStep(new CreateResourceRecordSetStep(dnsCow, gcpProjectConfig), RETRY_RULE);
    addStep(new FinishResourceCreationStep(bufferDao), RETRY_RULE);
  }
}
