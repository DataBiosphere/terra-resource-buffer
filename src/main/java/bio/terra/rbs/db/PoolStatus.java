package bio.terra.rbs.db;

/**
 * The state of the {@link Pool}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum PoolStatus {
  /** Active pool, able to handout resources. */
  ACTIVE,
  /** Inactive pool, all resources are deleted or being deleted, not able to handout resources. */
  INACTIVE,
}
