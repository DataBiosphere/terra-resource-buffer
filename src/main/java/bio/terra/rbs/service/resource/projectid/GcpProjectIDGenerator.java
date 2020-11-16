package bio.terra.rbs.service.resource.projectid;

import bio.terra.rbs.generated.model.ProjectIDGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates Project ID for GCP project from config. */
@Component
public class GcpProjectIDGenerator {
  /** The size of project when generating random characters. */
  @VisibleForTesting static final int RANDOM_ID_SIZE = 8;

  /**
   * Generates project Id from prefix and scheme.
   *
   * <p>The output will be prefix + naming scheme. For now, we only support RANDOM_CHAR scheme, and
   * that is also the default scheme if not set in resource config.
   */
  public String generateID(ProjectIDGenerator projectIDGenerator) {
    String generatedId = projectIDGenerator.getProjectIDPrefix();
    if (!generatedId.endsWith("-")) {
      generatedId = generatedId + "-";
    }

    switch (projectIDGenerator.getProjectIDScheme()) {
      case RANDOM_CHAR:
      default:
        generatedId += generateRandomId();
        break;
    }
    return generatedId;
  }

  private String generateRandomId() {
    return Hashing.sha256()
        .hashUnencodedChars(UUID.randomUUID().toString())
        .toString()
        .substring(0, RANDOM_ID_SIZE);
  }
}
