package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.createLogBucket;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.StorageRoles;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the storage log bucket where workspace bucket logs will be written to. Rawls creates
 * workspace buckets and enable logging to use this bucket as written location. We are not sure what
 * we will do in McTerra. Leave it as it, but eventually, this might be a CWB specific config, or
 * workspace bucket + storage log bucket is the general bundle for all workspaces.
 */
public class CreateStorageLogBucketStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateStorageLogBucketStep.class);

  /** The group that need the bucket access. */
  public static final Identity STORAGE_LOGS_IDENTITY =
      Identity.group("cloud-storage-analytics@google.com");

  /** Delete after 180 days. */
  public static final BucketInfo.LifecycleRule STORAGE_LOGS_LIFECYCLE_RULE =
      new BucketInfo.LifecycleRule(
          BucketInfo.LifecycleRule.LifecycleAction.newDeleteAction(),
          BucketInfo.LifecycleRule.LifecycleCondition.newBuilder().setAge(180).build());

  private final ClientConfig clientConfig;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateStorageLogBucketStep(ClientConfig clientConfig, GcpProjectConfig gcpProjectConfig) {
    this.clientConfig = clientConfig;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    if (!createLogBucket(gcpProjectConfig)) {
      logger.info("Skipping log bucket creation due to configuration parameter.");
      return StepResult.getStepResultSuccess();
    }
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    StorageCow storageCow =
        new StorageCow(clientConfig, StorageOptions.newBuilder().setProjectId(projectId).build());
    String bucketName = "storage-logs-" + projectId;
    if (storageCow.get(bucketName) != null) {
      return StepResult.getStepResultSuccess();
    }
    storageCow.create(
        BucketInfo.newBuilder(bucketName)
            .setLifecycleRules(ImmutableList.of(STORAGE_LOGS_LIFECYCLE_RULE))
            .build());

    Policy originalPolicy = storageCow.getIamPolicy(bucketName);
    storageCow.setIamPolicy(
        bucketName,
        originalPolicy.toBuilder()
            .addIdentity(StorageRoles.legacyBucketWriter(), STORAGE_LOGS_IDENTITY)
            .build());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP.
    return StepResult.getStepResultSuccess();
  }
}
