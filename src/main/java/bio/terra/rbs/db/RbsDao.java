package bio.terra.rbs.db;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static bio.terra.rbs.app.configuration.BeanNames.OBJECT_MAPPER;

@Component
public class RbsDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public RbsDao(NamedParameterJdbcTemplate jdbcTemplate, @Qualifier(OBJECT_MAPPER) ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Creates the tracked_resource record and adding labels.
   *
   * <p>Note that we assume int input {@code cloudResourceUid} is valid.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createPool(PoolConfig poolConfig, ResourceConfig resourceConfig) {
    String sql =
        "INSERT INTO tracked_resource (id, resource_uid, resource_type, creation, expiration, state) values "
            + "(:id, :resource_uid::jsonb, :resource_type, :creation, :expiration, :state)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", resource.trackedResourceId().uuid())
            .addValue("resource_uid", serialize(resourceConfig))
            .addValue("size", poolConfig.getSize())
            .addValue("creation", OffsetDateTime.now(ZoneOffset.UTC))
            .addValue("status", "ACTIVATE")
            .addValue("resource_config", serialize(resourceConfig));
  }

  /**
   * Creates the tracked_resource record and adding labels.
   *
   * <p>Note that we assume int input {@code cloudResourceUid} is valid.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void upgradePool() {}

  /** Serializes {@link ResourceConfig} into json format string. */
  private String serialize(ResourceConfig resourceConfig) {
    try {
      return objectMapper.writeValueAsString(resourceConfig);
    } catch (JsonProcessingException e) {
      throw new InvalidPoolConfigException("Failed to serialize CloudResourceUid");
    }
  }
}
