package bio.terra.rbs.service.resource.projectid;

import static bio.terra.rbs.service.resource.projectid.GcpProjectIDGenerator.RANDOM_ID_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.generated.model.ProjectIDGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GcpProjectIDGeneratorTest extends BaseUnitTest {
  @Autowired GcpProjectIDGenerator gcpProjectIDGenerator;

  @Test
  public void generateId_randomChar() {
    ProjectIDGenerator generatorConfig =
        new ProjectIDGenerator()
            .projectIDPrefix("prefix")
            .projectIDScheme(ProjectIDGenerator.ProjectIDSchemeEnum.RANDOM_CHAR);
    String generatedID = gcpProjectIDGenerator.generateID(generatorConfig);
    assertTrue(generatedID.startsWith("prefix-"));
    assertEquals(RANDOM_ID_SIZE, generatedID.substring("prefix-".length()).length());
  }

  @Test
  public void generateId_defaultSchema() {
    ProjectIDGenerator generatorConfig = new ProjectIDGenerator().projectIDPrefix("prefix");
    String generatedID = gcpProjectIDGenerator.generateID(generatorConfig);
    assertTrue(generatedID.startsWith("prefix-"));
    assertEquals(RANDOM_ID_SIZE, generatedID.substring("prefix-".length()).length());
  }
}
