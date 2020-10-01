package bio.terra.rbs.db;


/**
 * Enums to represent cloud resource type we supported in DB schema.
 *
 * <p>These enums are recorded as strings in the database and should therefore not be removed or
 * modified.
 *
 * <p>It is ok to add new values. Also update the BackwardsCompatibilityTest.
 */
public enum ResourceType {
    GOOGLE_PROJECT,
    GKE_CLUSTER,
}