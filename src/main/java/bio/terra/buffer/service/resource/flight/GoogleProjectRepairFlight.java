package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import org.springframework.context.ApplicationContext;

import static bio.terra.buffer.service.resource.FlightMapKeys.RESOURCE_CONFIG;
import static bio.terra.buffer.service.resource.flight.StepUtils.newCloudApiDefaultRetryRule;

/** {@link Flight} to re-enable GCP project APIs. */
public class GoogleProjectRepairFlight extends Flight {

  public GoogleProjectRepairFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ServiceUsageCow serviceUsageCow =
        ((ApplicationContext) applicationContext).getBean(ServiceUsageCow.class);
    GcpProjectConfig gcpProjectConfig =
        inputParameters.get(RESOURCE_CONFIG, ResourceConfig.class).getGcpProjectConfig();

    addStep(
        new EnableServicesStep(serviceUsageCow, gcpProjectConfig), newCloudApiDefaultRetryRule());
    // TODO: check that the storage log bucket exists and create it if not?
  }
}
