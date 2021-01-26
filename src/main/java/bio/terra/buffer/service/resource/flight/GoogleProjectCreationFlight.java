package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.buffer.service.resource.flight.StepUtils.CLOUD_API_DEFAULT_RETRY;
import static bio.terra.buffer.service.resource.flight.StepUtils.INTERNAL_DEFAULT_RETRY;

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
    addStep(new AssertResourceCreatingStep(bufferDao), INTERNAL_DEFAULT_RETRY);
    addStep(new UndoCreatingDbEntityStep(bufferDao), INTERNAL_DEFAULT_RETRY);
    addStep(new GenerateProjectIdStep(gcpProjectConfig, idGenerator), CLOUD_API_DEFAULT_RETRY);
    addStep(new CreateProjectStep(rmCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new SetBillingInfoStep(billingCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new EnableServicesStep(gcpProjectConfig, clientConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new SetIamPolicyStep(rmCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(
        new CreateStorageLogBucketStep(clientConfig, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new DeleteDefaultServiceAccountStep(iamCow), CLOUD_API_DEFAULT_RETRY);
    addStep(new DeleteDefaultFirewallRulesStep(cloudComputeCow), CLOUD_API_DEFAULT_RETRY);
    addStep(
        new DeleteDefaultNetworkStep(cloudComputeCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new CreateNetworkStep(cloudComputeCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new CreateRouteStep(cloudComputeCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new CreateFirewallRuleStep(cloudComputeCow), CLOUD_API_DEFAULT_RETRY);
    addStep(new CreateSubnetsStep(cloudComputeCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(
        new CreateDnsZoneStep(cloudComputeCow, dnsCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new CreateResourceRecordSetStep(dnsCow, gcpProjectConfig), CLOUD_API_DEFAULT_RETRY);
    addStep(new FinishResourceCreationStep(bufferDao), INTERNAL_DEFAULT_RETRY);
  }
}
