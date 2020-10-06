package bio.terra.rbs.service.pool;

import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.google.auto.value.AutoValue;

/** Represents a {@link PoolConfig} with corresponding {@link ResourceConfig}. */
@AutoValue
abstract class PoolWithResourceConfig {
  abstract PoolConfig poolConfig();

  abstract ResourceConfig resourceConfig();

  static PoolWithResourceConfig create(PoolConfig poolConfig, ResourceConfig resourceConfig) {
    return new AutoValue_PoolWithResourceConfig(poolConfig, resourceConfig);
  }
}
