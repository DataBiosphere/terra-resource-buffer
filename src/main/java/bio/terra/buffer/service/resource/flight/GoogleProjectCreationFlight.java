package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.buffer.service.resource.flight.StepUtils.newCloudApiDefaultRetryRule;
import static bio.terra.buffer.service.resource.flight.StepUtils.newInternalDefaultRetryRule;

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
    addStep(new AssertResourceCreatingStep(bufferDao), newInternalDefaultRetryRule());
    addStep(new UndoCreatingDbEntityStep(bufferDao), newInternalDefaultRetryRule());
    addStep(
        new GenerateProjectIdStep(gcpProjectConfig, idGenerator, rmCow),
        newCloudApiDefaultRetryRule());
    addStep(new CreateProjectStep(rmCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(new SetBillingInfoStep(billingCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(
        new EnableServicesStep(serviceUsageCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(new SetIamPolicyStep(rmCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(
        new CreateStorageLogBucketStep(clientConfig, gcpProjectConfig),
        newCloudApiDefaultRetryRule());
    addStep(
        new DeleteDefaultServiceAccountStep(iamCow, gcpProjectConfig),
        newCloudApiDefaultRetryRule());
    addStep(new DeleteDefaultFirewallRulesStep(cloudComputeCow), newCloudApiDefaultRetryRule());
    addStep(
        new DeleteDefaultNetworkStep(cloudComputeCow, gcpProjectConfig),
        newCloudApiDefaultRetryRule());
    addStep(
        new CreateNetworkStep(cloudComputeCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(new CreateRouteStep(cloudComputeCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(
        new CreateFirewallRuleStep(cloudComputeCow, gcpProjectConfig),
        newCloudApiDefaultRetryRule());
    addStep(
        new CreateSubnetsStep(cloudComputeCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(
        new CreateDnsZoneStep(cloudComputeCow, dnsCow, gcpProjectConfig),
        newCloudApiDefaultRetryRule());
    addStep(
        new CreateGkeDefaultSAStep(iamCow, rmCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(
        new CreateResourceRecordSetStep(dnsCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    addStep(new FinishResourceCreationStep(bufferDao), newInternalDefaultRetryRule());
  }
}
