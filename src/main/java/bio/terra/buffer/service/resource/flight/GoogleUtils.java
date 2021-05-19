package bio.terra.buffer.service.resource.flight;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.exception.RetryException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/** Utilities when use Google APIs. */
public class GoogleUtils {
  /** All project will use the same network name. */
  @VisibleForTesting public static final String NETWORK_NAME = "network";

  /** The private DNS zone name. */
  @VisibleForTesting
  public static final String MANAGED_ZONE_NAME = "private-google-access-dns-zone";

  /** All project will use the same sub network name. */
  @VisibleForTesting public static final String SUBNETWORK_NAME = "subnetwork";

  /** The name of the default network that exists in the project. */
  public static final String DEFAULT_NETWORK_NAME = "default";

  /**
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }

  /** Retrieves a project by id. Returns {@code Optional.empty} for 403 error code. */
  public static Optional<Project> retrieveProject(CloudResourceManagerCow rmCow, String projectId)
      throws IOException {
    try {
      return Optional.of(rmCow.projects().get(projectId).execute());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 403) {
        // Google returns 403 for projects we don't have access to and projects that don't exist.
        // We assume in this case that the project does not exist, not that somebody else has
        // created a project with the same random id.
        return Optional.empty();
      }
      throw e;
    }
  }

  /** Converts project id to name. */
  public static String projectIdToName(String projectId) {
    return "projects/" + projectId;
  }

  /**
   * Checks if cloudObject already exists.
   *
   * <p>Many Google 'get' operations return a {@link GoogleJsonResponseException} 404. This function
   * handles that exception case as false, while throwing any other exception.
   */
  public static <R> boolean resourceExists(CloudExecute<R> execute, int acceptable)
      throws IOException {
    return getResource(execute, acceptable).isPresent();
  }

  /** See {@link GoogleUtils#resourceExists(CloudExecute, int)}. */
  public static <R> Optional<R> getResource(CloudExecute<R> execute, int acceptable)
      throws IOException {
    try {
      return Optional.of(execute.execute());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == acceptable) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  /** Creates cloud resources and ignore conflict error(409). */
  public static <R> Optional<R> createResourceAndIgnoreConflict(CloudExecute<R> execute)
      throws IOException {
    try {
      return Optional.of(execute.execute());
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 409) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  /** Checks if project is being deleted. */
  public static boolean isProjectDeleting(Project project) {
    return project.getState().equals("DELETE_REQUESTED")
        || project.getState().equals("DELETE_IN_PROGRESS");
  }

  /**
   * A Google cloud operation that's expected to throw a IOException. See {@link
   * GoogleUtils#resourceExists}.
   */
  @FunctionalInterface
  public interface CloudExecute<R> {
    R execute() throws IOException;
  }
}
