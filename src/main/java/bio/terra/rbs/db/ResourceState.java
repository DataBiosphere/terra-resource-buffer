package bio.terra.rbs.db;

/**
 * The state of the {@link Pool}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum ResourceState {
  /** Resource is creating. */
  CREATING,
  /** Resource is ready to handout. */
  READY,
  /** Resource is handed out. */
  HANDEDOUT,
}
