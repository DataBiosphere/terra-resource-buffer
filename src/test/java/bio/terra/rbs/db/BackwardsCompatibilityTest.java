package bio.terra.rbs.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.rbs.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests to verify that changes to Java classes do not break backwards compatibility with
 * values that might already exist in databases.
 *
 * <p>If your change causes one of these tests to fail, consider whether it is backwards compatible
 * instead of modifying the test to pass.
 */
public class BackwardsCompatibilityTest extends BaseUnitTest {

  /**
   * Change detection test for existing {@link ResourceType} enum values. More values should be
   * added as the enum expands.
   */
  @Test
  public void resourceType() {
    // Make sure we won't forget to modify this test when we add/remove enums.
    assertEquals(1, ResourceType.values().length);

    assertEquals(ResourceType.GOOGLE_PROJECT, ResourceType.valueOf("GOOGLE_PROJECT"));
  }

  /**
   * Change detection test for existing {@link PoolStatus} enum values. More values should be added
   * as the enum expands.
   */
  @Test
  public void poolStatus() {
    // Make sure we won't forget to modify this test when we add/remove enums.
    assertEquals(2, PoolStatus.values().length);

    assertEquals(PoolStatus.ACTIVE, PoolStatus.valueOf("ACTIVE"));
    assertEquals(PoolStatus.DEACTIVATED, PoolStatus.valueOf("DEACTIVATED"));
  }

  /**
   * Change detection test for existing {@link ResourceState} enum values. More values should be
   * added as the enum expands.
   */
  @Test
  public void resourceState() {
    // Make sure we won't forget to modify this test when we add/remove enums.
    assertEquals(3, ResourceState.values().length);

    assertEquals(ResourceState.CREATING, ResourceState.valueOf("CREATING"));
    assertEquals(ResourceState.READY, ResourceState.valueOf("READY"));
    assertEquals(ResourceState.USED, ResourceState.valueOf("USED"));
  }
}
