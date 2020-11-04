package bio.terra.rbs.service.resource.flight;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.rbs.service.resource.flight.GoogleUtils.*;
import static bio.terra.rbs.service.resource.flight.StepUtils.isResourceReady;

/** Creates the basic GCP project. */
public class CreateBucketStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateBucketStep.class);
  private final ClientConfig clientConfig;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateBucketStep(ClientConfig clientConfig, GcpProjectConfig gcpProjectConfig) {
    this.clientConfig = clientConfig;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    StorageCow storageCow = new StorageCow(clientConfig, StorageOptions.newBuilder().setProjectId(projectId).build());
    String bucketName = "storage-logs-" + projectId;
    if(storageCow.get(bucketName) != null) {
      return StepResult.getStepResultSuccess();
    }
    BucketInfo.LifecycleRule rule = new BucketInfo.LifecycleRule(BucketInfo.LifecycleRule.LifecycleAction.newDeleteAction(),
            BucketInfo.LifecycleRule.LifecycleCondition.newBuilder().setAge(180).build());
    Acl acl = Acl.newBuilder().setEntity().build()
    storageCow.create(BucketInfo.newBuilder(bucketName).setLifecycleRules(ImmutableList.of(rule)).build());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP.
    return StepResult.getStepResultSuccess();
  }
}
