package bio.terra.buffer.service.resource.projectid;

import static bio.terra.buffer.service.resource.flight.GoogleUtils.retrieveProject;

import bio.terra.buffer.generated.model.ProjectIdSchema;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Generates Project Id for GCP project from config. */
@Component
public class GcpProjectIdGenerator {
  private final Logger logger = LoggerFactory.getLogger(GcpProjectIdGenerator.class);

  /**
   * The size of project when generating random characters. Choose size as 8 based on AoU's
   * historical experience, increase if 8 is not enough for a pool's naming.
   */
  @VisibleForTesting static final int RANDOM_ID_SIZE = 8;

  /** The largest number to use for random suffixes for adjective/noun project IDs. */
  @VisibleForTesting static final int RANDOM_SUFFIX_LIMIT = 10000;

  /** The maximum allowed length for a GCP project id. */
  private static final int MAX_LENGTH_GCP_PROJECT_ID = 30;

  /**
   * The maximum allowed length for a GCP project id prefix (e.g. "terra-") when using the
   * TWO_WORDS_NUMBER naming scheme.
   */
  public static final int MAX_LENGTH_GCP_PROJECT_ID_PREFIX = 12;

  /**
   * The maximum number of times to try generating a project id that has an allowable length and is
   * not already in use.
   */
  private static final int MAX_RETRIES = 100;

  /**
   * Generate a GCP project id from the prefix and scheme.
   *
   * <p>Keep retrying the id generation until we find one that fits into the GCP project id length
   * limit and is not already in use, or until we have retried the maximum number of times.
   *
   * <p>To check if a project is in use, this method tries to retrieve the project. This will only
   * succeed if RBS has permission to get that project. We expect this to be the case most of the
   * time because of the Terra-specific prefix, but it's possible this step will generate a project
   * that is in use outside of Terra. In that case, the project creation step will fail.
   *
   * @param projectIdSchema prefix and naming scheme
   * @param rmCow resource manager wrapper to retrieve the project
   * @return generated project id (prefix + naming scheme)
   * @throws IOException if there is an error retrieving the project from GCP
   */
  public String generateIdWithRetries(
      ProjectIdSchema projectIdSchema, CloudResourceManagerCow rmCow) throws IOException {
    for (int numTries = 0; numTries < MAX_RETRIES; numTries++) {
      String projectId = generateId(projectIdSchema);
      if (projectId.length() <= MAX_LENGTH_GCP_PROJECT_ID)
        if (retrieveProject(rmCow, projectId).isEmpty()) {
          logger.info("Generated GCP project id after {} tries.", numTries);
          return projectId;
        } else {
          logger.info("Generated GCP project is already in use: {}", projectId);
        }
    }
    throw new RuntimeException(
        "No project id found after maximum number of retries: " + MAX_RETRIES);
  }

  @VisibleForTesting
  /**
   * Generates project Id from prefix and scheme.
   *
   * <p>The output will be prefix + naming scheme.
   */
  public String generateId(ProjectIdSchema projectIdSchema) {
    String generatedId = projectIdSchema.getPrefix();
    if (!generatedId.endsWith("-")) {
      generatedId = generatedId + "-";
    }

    switch (projectIdSchema.getScheme()) {
      case TWO_WORDS_NUMBER:
        generatedId += WordPairs.getWithRandomSuffix();
        break;

      case RANDOM_CHAR:
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

  private static class WordPairs {
    static final List<String> words = loadWords();
    static final Random random = new Random();

    static List<String> loadWords() {
      List<String> words = new ArrayList<String>();
      Scanner scanner =
          new Scanner(WordPairs.class.getResourceAsStream("/random_project_prefixes.txt"));
      while (scanner.hasNext()) {
        words.add(scanner.next());
      }
      return words;
    }

    static String getWithRandomSuffix() {
      return words.get(random.nextInt(words.size())) + "-" + random.nextInt(RANDOM_SUFFIX_LIMIT);
    }
  }
}
