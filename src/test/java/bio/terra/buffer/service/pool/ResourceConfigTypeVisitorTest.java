package bio.terra.buffer.service.pool;

import static bio.terra.buffer.common.ResourceType.GOOGLE_PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.common.ResourceConfigVisitor;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
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
