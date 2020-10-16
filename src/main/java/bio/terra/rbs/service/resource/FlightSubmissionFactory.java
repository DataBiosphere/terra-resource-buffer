package bio.terra.rbs.service.resource;

import bio.terra.rbs.common.Pool;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.common.ResourceType;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.auto.value.AutoValue;
import org.springframework.stereotype.Component;

/** An interface getting {@link Flight} from {@link ResourceType}. */
@Component
public interface FlightSubmissionFactory {
  FlightSubmission getCreationFlightSubmission(Pool pool);

  FlightSubmission getDeletionFlightSubmission(Resource resource, ResourceType type);

  /** A value class of the parameters needed to submit a new flight to Stairway. */
  @AutoValue
  abstract class FlightSubmission {
    public abstract Class<? extends Flight> clazz();

    public abstract FlightMap inputParameters();

    public static FlightSubmission create(
        Class<? extends Flight> clazz, FlightMap inputParameters) {
      return new AutoValue_FlightSubmissionFactory_FlightSubmission(clazz, inputParameters);
    }
  }
}
