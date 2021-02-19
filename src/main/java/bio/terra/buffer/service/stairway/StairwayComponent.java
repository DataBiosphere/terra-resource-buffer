package bio.terra.buffer.service.stairway;

import static com.google.cloud.ServiceOptions.getDefaultProjectId;

import bio.terra.buffer.app.configuration.StairwayConfiguration;
import bio.terra.buffer.app.configuration.StairwayJdbcConfiguration;
import bio.terra.buffer.service.kubernetes.KubernetesComponent;
import bio.terra.common.kubernetes.KubeService;
import bio.terra.common.stairway.TracingHook;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.stairway.exception.StairwayExecutionException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** A Spring Component for exposing an initialized {@link Stairway}. */
@Component
public class StairwayComponent {
  private final Logger logger = LoggerFactory.getLogger(StairwayComponent.class);

  private final StairwayConfiguration stairwayConfiguration;
  private final StairwayJdbcConfiguration stairwayJdbcConfiguration;
  private final Stairway stairway;
  private final KubeService kubeService;

  public enum Status {
    INITIALIZING,
    OK,
    ERROR,
    SHUTDOWN,
  }

  private final AtomicReference<Status> status = new AtomicReference<>(Status.INITIALIZING);

  @Autowired
  public StairwayComponent(
      ApplicationContext applicationContext,
      StairwayConfiguration stairwayConfiguration,
      StairwayJdbcConfiguration stairwayJdbcConfiguration,
      KubernetesComponent kubernetesComponent) {
    this.stairwayConfiguration = stairwayConfiguration;
    this.stairwayJdbcConfiguration = stairwayJdbcConfiguration;
    this.kubeService = kubernetesComponent.get();
    String stairwayClusterName = kubeService.getNamespace() + "buffer--stairwaycluster";
    logger.info(
        "Creating Stairway: name: [{}]  cluster name: [{}]",
        kubeService.getPodName(),
        stairwayClusterName);

    // TODO(PF-314): Cleanup old flightlogs.
    // TODO(PF-456):
    Stairway.Builder builder =
        Stairway.newBuilder()
            .maxParallelFlights(stairwayConfiguration.getMaxParallelFlights())
            .applicationContext(applicationContext)
            .keepFlightLog(true)
            .stairwayName(kubeService.getPodName())
            .stairwayClusterName(stairwayClusterName)
            .workQueueProjectId(getDefaultProjectId())
            .enableWorkQueue(kubernetesComponent.isInKubernetes())
            .stairwayHook(new TracingHook());
    try {
      stairway = builder.build();
    } catch (StairwayExecutionException e) {
      throw new IllegalArgumentException("Failed to build Stairway.", e);
    }
  }

  public void initialize() {
    logger.info("Initializing Stairway...");
    logger.info("stairway username {}", stairwayJdbcConfiguration.getUsername());
    try {
      // TODO(PF-161): Determine if Stairway and buffer database migrations need to be coordinated.
      List<String> recordedStairways =
          stairway.initialize(
              stairwayJdbcConfiguration.getDataSource(),
              stairwayConfiguration.isForceCleanStart(),
              stairwayConfiguration.isMigrateUpgrade());

      kubeService.startPodListener(stairway);

      // Lookup all of the stairway instances we know about
      Set<String> existingStairways = kubeService.getPodSet();
      List<String> obsoleteStairways = new LinkedList<>();

      // Any instances that stairway knows about, but we cannot see are obsolete.
      for (String recordedStairway : recordedStairways) {
        if (!existingStairways.contains(recordedStairway)) {
          obsoleteStairways.add(recordedStairway);
        }
      }

      // Add our own pod name to the list of obsolete stairways. Sometimes Kubernetes will
      // restart the container without redeploying the pod. In that case we must ask
      // Stairway to recover the flights we were working on before being restarted.
      obsoleteStairways.add(kubeService.getPodName());

      logger.info(
          "existingStairways: {}. obsoleteStairways: {}", existingStairways, obsoleteStairways);
      // Recover and start stairway - step 3 of the stairway startup sequence
      stairway.recoverAndStart(obsoleteStairways);
    } catch (StairwayException | InterruptedException e) {
      status.compareAndSet(Status.INITIALIZING, Status.ERROR);
      throw new RuntimeException("Error starting Stairway", e);
    }
    status.compareAndSet(Status.INITIALIZING, Status.OK);
  }

  /** Stop accepting jobs and shutdown stairway. Returns true if successful. */
  public boolean shutdown() throws InterruptedException {
    status.set(Status.SHUTDOWN);
    logger.info("Request Stairway shutdown");
    boolean shutdownSuccess =
        stairway.quietDown(
            stairwayConfiguration.getQuietDownTimeout().toMillis(), TimeUnit.MILLISECONDS);
    if (!shutdownSuccess) {
      logger.info("Request Stairway terminate");
      shutdownSuccess =
          stairway.terminate(
              stairwayConfiguration.getTerminateTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
    logger.info("Finished Stairway shutdown?: {}", shutdownSuccess);
    return shutdownSuccess;
  }

  public Stairway get() {
    return stairway;
  }

  public StairwayComponent.Status getStatus() {
    return status.get();
  }
}
