package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
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

  /** Assigns Cloud Storage writer role for storage log bucket. */
  public static final Acl STORAGE_LOGS_WRITE_ACL =
      Acl.newBuilder(new Acl.Group("cloud-storage-analytics@google.com"), Acl.Role.WRITER).build();

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
            .setAcl(ImmutableList.of(STORAGE_LOGS_WRITE_ACL))
            .build());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP.
    return StepResult.getStepResultSuccess();
  }
}
