package bio.terra.rbs.db;

import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.auto.value.AutoValue;

import java.time.Instant;

/** Represents a record in the pool table in the RBS's database. */
@AutoValue
public abstract class PoolSchema {
    public abstract PoolId id();

    public abstract String name();

    public abstract ResourceConfig resourceConfig();

    public abstract ResourceType resourceType();

    public abstract PoolStatus status();

    public abstract int size();

    public abstract Instant creation();

    public abstract Instant expiration();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(PoolId id);

        public abstract Builder name(String name);

        public abstract Builder resourceConfig(ResourceConfig resourceConfig);

        public abstract Builder resourceType(ResourceType resourceType);

        public abstract Builder status(PoolStatus status);

        public abstract Builder size(int size);

        public abstract Builder creation(Instant creation);

        public abstract Builder expiration(Instant expiration);

        public abstract PoolSchema build();
    }
}
