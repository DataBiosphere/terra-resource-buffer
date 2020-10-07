package bio.terra.rbs.db;

import static bio.terra.rbs.app.configuration.BeanNames.OBJECT_MAPPER;

import bio.terra.rbs.common.exception.InvalidPoolConfigException;
import bio.terra.rbs.generated.model.ResourceConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RBS Database data access object. */
@Component
public class RbsDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public RbsDao(
      NamedParameterJdbcTemplate jdbcTemplate,
      @Qualifier(OBJECT_MAPPER) ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Creates the pool record and adding labels.
   *
   * <p>Note that we assume the nested {@link ResourceConfig} is valid.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createPools(List<Pool> pools) {
    String sql =
        "INSERT INTO pool (id, resource_type, resource_config, size, creation, status) values "
            + "(:id, :resource_type, :resource_config::jsonb, :size, :creation, :status)";

    MapSqlParameterSource[] sqlParameterSourceList =
        pools.stream()
            .map(
                pool ->
                    new MapSqlParameterSource()
                        .addValue("id", pool.id().toString())
                        .addValue("resource_type", pool.resourceType().toString())
                        .addValue("resource_config", serialize(pool.resourceConfig()))
                        .addValue("size", pool.size())
                        .addValue("creation", pool.creation().atOffset(ZoneOffset.UTC))
                        .addValue("status", pool.status().toString()))
            .toArray(MapSqlParameterSource[]::new);

    jdbcTemplate.batchUpdate(sql, sqlParameterSourceList);
  }

  /** Retrieves all pools. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<Pool> retrievePools() {
    // TODO: Add filter
    String sql =
        "select p.id, p.resource_config, p.resource_type, p.creation, p.size, p.status "
            + "FROM pool p ";

    return jdbcTemplate.query(sql, POOL_ROW_MAPPER);
  }

  /** Retrieves all pools and READY resource count in each pool. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<PoolAndResourceStates> retrievePoolAndResourceStatesCount() {
    // TODO: Add filter
    String sql =
        "select count (*) FILTER (WHERE r.state='READY') as ready_count, "
            + "count(*) FILTER (WHERE r.state='CREATING') as creating_count, "
            + "p.id, p.resource_config, p.resource_type, p.creation, p.size, p.status "
            + "FROM resource r "
            + "RIGHT JOIN pool p on r.pool_id = p.id "
            + "GROUP BY p.id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("state", ResourceState.READY.toString());

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) ->
            PoolAndResourceStates.create(
                POOL_ROW_MAPPER.mapRow(rs, rowNum),
                getResourceStateCountSet(rs.getInt("ready_count"), rs.getInt("creating_count"))));
  }

  /** Updates list of pools' status to DEACTIVATED. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void deactivatePools(List<PoolId> poolIds) {
    String sql = "UPDATE pool SET status = :status, expiration = :expiration where id = :id ";

    MapSqlParameterSource[] sqlParameterSourceList =
        poolIds.stream()
            .map(
                poolId ->
                    new MapSqlParameterSource()
                        .addValue("id", poolId.id())
                        .addValue("status", PoolStatus.DEACTIVATED.toString())
                        .addValue("expiration", OffsetDateTime.now(ZoneOffset.UTC)))
            .toArray(MapSqlParameterSource[]::new);

    jdbcTemplate.batchUpdate(sql, sqlParameterSourceList);
  }

  /** Updates list of pools' size. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void updatePoolsSize(Map<PoolId, Integer> poolsToUpdateSize) {
    String sql = "UPDATE pool SET size = :size where id = :id ";

    MapSqlParameterSource[] sqlParameterSourceList =
        poolsToUpdateSize.entrySet().stream()
            .map(
                entry ->
                    new MapSqlParameterSource()
                        .addValue("id", entry.getKey().id())
                        .addValue("size", entry.getValue()))
            .toArray(MapSqlParameterSource[]::new);

    jdbcTemplate.batchUpdate(sql, sqlParameterSourceList);
  }

  private static final RowMapper<Pool> POOL_ROW_MAPPER =
      (rs, rowNum) ->
          Pool.builder()
              .id(PoolId.create(rs.getString("id")))
              .resourceConfig(deserialize(rs.getString("resource_config")))
              .resourceType(ResourceType.valueOf(rs.getString("resource_type")))
              .status(PoolStatus.valueOf(rs.getString("status")))
              .size(rs.getInt("size"))
              .creation(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .build();

  /** Serializes {@link ResourceConfig} into json format string. */
  private static String serialize(ResourceConfig resourceConfig) {
    try {
      return new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .writeValueAsString(resourceConfig);
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

  private static Multiset<ResourceState> getResourceStateCountSet(
      int readyCount, int creatingCount) {
    Multiset<ResourceState> result = HashMultiset.create();
    result.add(ResourceState.READY, readyCount);
    result.add(ResourceState.CREATING, creatingCount);
    return result;
  }
}
