package bio.terra.rbs.db;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.generated.model.PoolConfig;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
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
  public void createPool(Pool pool) {
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

  /** Retrieves all active pools.*/
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<Pool> retrieveActivePools() {
    String sql =
            "select p.id, p.name, p.resource_config_name, p.resource_type, p.creation, p.expiration, p.size "
                    + "FROM pool "
                    + "WHERE p.status = :pool_status";

    MapSqlParameterSource params =
            new MapSqlParameterSource()
                    .addValue("pool_status", PoolStatus.ACTIVE.toString());

    return jdbcTemplate.query(
            sql,
            params,
            POOL_ROW_MAPPER);
  }

  /**
   * Creates the tracked_resource record and adding labels.
   *
   * <p>Note that we assume int input {@code cloudResourceUid} is valid.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void upgradePool() {}



  private static final RowMapper<Pool> POOL_ROW_MAPPER =
          (rs, rowNum) ->
                  Pool.builder()
                          .id(PoolId.create(rs.getObject("id", UUID.class)))
                          .name(rs.getString("name"))
                          .resourceConfig(deserialize(rs.getString("resource_config")))
                          .resourceType(ResourceType.valueOf(rs.getString("resource_type")))
                          .status(PoolStatus.valueOf(rs.getString("status")))
                          .size(rs.getInt("size"))
                          .creation(rs.getObject("creation", OffsetDateTime.class).toInstant())
                          .expiration(rs.getObject("expiration", OffsetDateTime.class).toInstant())
                          .build();

  /** Serializes {@link ResourceConfig} into json format string. */
  private static String serialize(ResourceConfig resourceConfig) {
    try {
      return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).writeValueAsString(resourceConfig);
    } catch (JsonProcessingException e) {
      throw new InvalidPoolConfigException("Failed to serialize ResourceConfig");
    }
  }

  /** Deserializes {@link ResourceConfig} into json format string. */
  private static ResourceConfig deserialize(String resourceConfig) {
    try {
      return new ObjectMapper().readValue(resourceConfig, ResourceConfig.class);
    } catch (JsonProcessingException e) {
      throw new InvalidPoolConfigException("Failed to deserialize ResourceConfig");
    }
  }

}
