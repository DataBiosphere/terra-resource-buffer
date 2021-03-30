package bio.terra.buffer.service.resource.projectid;

import static bio.terra.buffer.generated.model.ProjectIdSchema.SchemeEnum.RANDOM_CHAR;
import static bio.terra.buffer.generated.model.ProjectIdSchema.SchemeEnum.TWO_WORDS_NUMBER;
import static bio.terra.buffer.service.resource.projectid.GcpProjectIdGenerator.RANDOM_ID_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.generated.model.ProjectIdSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GcpProjectIdGeneratorTest extends BaseUnitTest {
  @Autowired GcpProjectIdGenerator gcpProjectIDGenerator;

  @Test
  public void generateId_randomChar() {
    ProjectIdSchema generatorConfig = new ProjectIdSchema().prefix("prefix").scheme(RANDOM_CHAR);
    String generatedID = gcpProjectIDGenerator.generateId(generatorConfig);
    assertTrue(generatedID.startsWith("prefix-"));
    assertEquals(RANDOM_ID_SIZE, generatedID.substring("prefix-".length()).length());
  }

  @Test
  public void generateId_wordPair() {
    ProjectIdSchema generatorConfig =
        new ProjectIdSchema().prefix("prefix").scheme(TWO_WORDS_NUMBER);
    String generatedID = gcpProjectIDGenerator.generateId(generatorConfig);
    assertThat(generatedID, matchesPattern("prefix-[a-z]+-[a-z]+-[0-9]+"));
  }
}
