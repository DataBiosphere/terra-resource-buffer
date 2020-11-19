package bio.terra.buffer.service.pool;

import bio.terra.buffer.generated.model.PoolConfig;
import bio.terra.buffer.generated.model.ResourceConfig;
import com.google.auto.value.AutoValue;

/** Represents a {@link PoolConfig} with corresponding {@link ResourceConfig}. */
@AutoValue
public abstract class PoolWithResourceConfig {
  public abstract PoolConfig poolConfig();

  public abstract ResourceConfig resourceConfig();

  static PoolWithResourceConfig create(PoolConfig poolConfig, ResourceConfig resourceConfig) {
    return new AutoValue_PoolWithResourceConfig(poolConfig, resourceConfig);
  }
}
