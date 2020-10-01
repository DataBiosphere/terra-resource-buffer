package bio.terra.rbs.db;

/**
 * The state of the {@link PoolSchema}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum PoolStatus {
    ACTIVE,
    INACTIVE,
}
