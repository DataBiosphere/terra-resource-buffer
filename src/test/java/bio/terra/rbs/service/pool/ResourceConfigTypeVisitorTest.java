package bio.terra.rbs.service.pool;

import static bio.terra.rbs.common.ResourceType.GOOGLE_PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.common.ResourceConfigVisitor;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import org.junit.jupiter.api.Test;

public class ResourceConfigTypeVisitorTest extends BaseUnitTest {
  @Test
  public void visitGoogleProject() {
    assertEquals(
        GOOGLE_PROJECT,
        ResourceConfigVisitor.visit(
                new ResourceConfig().gcpProjectConfig(new GcpProjectConfig()),
                new ResourceConfigTypeVisitor())
            .get());
  }

  @Test
  public void visitNoResourceType() {
    assertFalse(
        ResourceConfigVisitor.visit(new ResourceConfig(), new ResourceConfigTypeVisitor())
            .isPresent());
  }
}
