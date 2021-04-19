package bio.terra.buffer.service.resource.projectid;

import bio.terra.buffer.generated.model.ProjectIdSchema;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates Project Id for GCP project from config. */
@Component
public class GcpProjectIdGenerator {
  /**
   * The size of project when generating random characters. Choose size as 8 based on AoU's
   * historical experience, increase if 8 is not enough for a pool's naming.
   */
  @VisibleForTesting static final int RANDOM_ID_SIZE = 8;

  /** The largest number to use for random suffixes for adjective/noun project IDs. */
  @VisibleForTesting static final int RANDOM_SUFFIX_LIMIT = 10000;

  /**
   * Generates project Id from prefix and scheme.
   *
   * <p>The output will be prefix + naming scheme. For now, we only support RANDOM_CHAR scheme.
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
