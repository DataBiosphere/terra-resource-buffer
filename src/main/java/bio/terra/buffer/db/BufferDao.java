package bio.terra.buffer.db;

import static bio.terra.buffer.app.configuration.BeanNames.OBJECT_MAPPER;

import bio.terra.buffer.common.*;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.service.resource.flight.CreateNetworkStep;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Resource Buffer Service Database data access object. */
@Component
public class BufferDao {
  private final Logger logger = LoggerFactory.getLogger(CreateNetworkStep.class);

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public BufferDao(
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
                        .addValue("resource_config", serializeResourceConfig(pool.resourceConfig()))
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

  /** Retrieves a pool with id. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<Pool> retrievePool(PoolId poolId) {
    String sql =
        "select p.id, p.resource_config, p.resource_type, p.creation, p.size, p.status "
            + "FROM pool p "
            + "WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", poolId.id());

    return Optional.ofNullable(
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, POOL_ROW_MAPPER)));
  }

  /** Retrieves all pools and resource count for each state. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<PoolAndResourceStates> retrievePoolAndResourceStates() {
    String sql =
        "select count(*) as resource_count, r.state, "
            + "p.id, p.resource_config, p.resource_type, p.creation, p.size, p.status "
            + "FROM pool p "
            + "LEFT JOIN resource r on r.pool_id = p.id "
            + "GROUP BY p.id, r.state";

    return jdbcTemplate.query(sql, new PoolAndResourceStatesExtractor());
  }

  /** Retrieves one pool's and resource count for each state. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<PoolAndResourceStates> retrievePoolAndResourceStatesById(PoolId poolId) {
    String sql =
        "select count(*) as resource_count, r.state, "
            + "p.id, p.resource_config, p.resource_type, p.creation, p.size, p.status "
            + "FROM pool p "
            + "LEFT JOIN resource r on r.pool_id = p.id "
            + "WHERE p.id = :id "
            + "GROUP BY p.id, r.state";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", poolId.id());

    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, new PoolAndResourceStatesExtractor())));
  }

  /** Updates list of pools' status to DEACTIVATED. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void deactivatePools(List<PoolId> poolIds) {
    String sql = "UPDATE pool SET status = :status, expiration = :expiration WHERE id = :id ";

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
    String sql = "UPDATE pool SET size = :size WHERE id = :id ";

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

  /** Updates list of pools' size. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createResource(Resource resource) {
    String sql =
        "INSERT INTO resource (id, pool_id, creation, state) values "
            + "(:id, :pool_id, :creation, :state)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", resource.id().id())
            .addValue("pool_id", resource.poolId().id())
            .addValue("creation", resource.creation().atOffset(ZoneOffset.UTC))
            .addValue("state", resource.state().toString());

    jdbcTemplate.update(sql, params);
  }

  /** Retrieve a resource by id. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<Resource> retrieveResource(ResourceId resourceId) {
    String sql =
        "select id, pool_id, creation, handout_time, state, request_handout_id, cloud_resource_uid, deletion "
            + "FROM resource "
            + "WHERE id = :id";

    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", resourceId.id());

    return Optional.ofNullable(
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, RESOURCE_ROW_MAPPER)));
  }

  /**
   * Retrieve resource by pool_id and request_handout_id. There should be at most one matched
   * resource for a request_handout_id in one pool.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<Resource> retrieveResource(PoolId poolId, RequestHandoutId requestHandoutId) {
    String sql =
        "select id, pool_id, creation, handout_time, state, request_handout_id, cloud_resource_uid, deletion "
            + "FROM resource "
            + "WHERE pool_id = :pool_id AND request_handout_id = :request_handout_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("pool_id", poolId.id())
            .addValue("request_handout_id", requestHandoutId.id());

    return Optional.ofNullable(
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, RESOURCE_ROW_MAPPER)));
  }

  /** Retrieve resources match the {@link ResourceState}. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public List<Resource> retrieveResources(PoolId poolId, ResourceState state, int limit) {
    String sql =
        "select id, pool_id, creation, handout_time, state, request_handout_id, cloud_resource_uid, deletion "
            + "FROM resource "
            + "WHERE state = :state AND pool_id = :pool_id "
            + "LIMIT :limit";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", state.toString())
            .addValue("pool_id", poolId.id())
            .addValue("limit", limit);

    return jdbcTemplate.query(sql, params, RESOURCE_ROW_MAPPER);
  }

  /** Updates resource state and resource uid after resource is created. */
  @CheckReturnValue
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean updateResourceAsReady(ResourceId id, CloudResourceUid resourceUid) {
    String sql =
        "UPDATE resource SET state = :state, cloud_resource_uid = :cloud_resource_uid::jsonb WHERE id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", ResourceState.READY.toString())
            .addValue("cloud_resource_uid", serializeResourceUid(resourceUid))
            .addValue("id", id.id());
    return jdbcTemplate.update(sql, params) == 1;
  }

  /**
   * Updates resource state, request_handout_id and handout_time before resource handed out to
   * client.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public boolean updateResourceAsHandedOut(ResourceId id, RequestHandoutId requestHandoutId) {
    String sql =
        "UPDATE resource SET state = :state, request_handout_id = :request_handout_id, handout_time = :handout_time"
            + " WHERE id = :id AND request_handout_id IS null";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", ResourceState.HANDED_OUT.toString())
            .addValue("request_handout_id", requestHandoutId.id())
            .addValue("handout_time", OffsetDateTime.now(ZoneOffset.UTC))
            .addValue("id", id.id());
    return jdbcTemplate.update(sql, params) == 1;
  }

  /**
   * Updates resource in READY state to DELETING. Returns true if previous state is READY and we
   * successfully update its state.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean updateReadyResourceToDeleting(ResourceId id) {
    String readSql =
        "select id, pool_id, creation, handout_time, state, request_handout_id, cloud_resource_uid, deletion "
            + "FROM resource WHERE id = :id ";
    MapSqlParameterSource readParams = new MapSqlParameterSource().addValue("id", id.id());
    Resource resource =
        DataAccessUtils.singleResult(jdbcTemplate.query(readSql, readParams, RESOURCE_ROW_MAPPER));
    if (resource == null || !resource.state().equals(ResourceState.READY)) {
      logger.warn("We shouldn't mark non-READY resource {} to DELETING", resource);
      return false;
    } else {
      String sql = "UPDATE resource SET state = :state WHERE id = :id";
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("state", ResourceState.DELETING.toString())
              .addValue("id", id.id());
      return jdbcTemplate.update(sql, params) == 1;
    }
  }

  /** Updates resource state and deletion timestamp after resource is deleted. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean updateResourceAsDeleted(ResourceId id, Instant deletedTime) {
    String sql = "UPDATE resource SET state = :state, deletion = :deletion WHERE id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", ResourceState.DELETED.toString())
            .addValue("deletion", OffsetDateTime.ofInstant(deletedTime, ZoneOffset.UTC))
            .addValue("id", id.id());
    return jdbcTemplate.update(sql, params) == 1;
  }

  /** Delete the resource match the {@link ResourceId}. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean deleteResource(ResourceId id) {
    String sql = "DELETE FROM resource WHERE id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id.id());

    return jdbcTemplate.update(sql, params) == 1;
  }

  /**
   * Inserts a record into cleanup_record table. A record will be inserted into clean_up table after
   * Resource Buffer Service publish this resource message to Janitor. This is only expected to be
   * used in testing environment to make sure resources can be cleaned up after use.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void insertCleanupRecord(ResourceId resourceId) {
    String sql = "INSERT INTO cleanup_record (resource_id) values (:resource_id)";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resource_id", resourceId.id());

    jdbcTemplate.update(sql, params);
  }

  /**
   * Retrieves resources that need to cleanup by Janitor. Those resources should be:
   *
   * <ul>
   *   <li>State is HANDED_OUT in resource table
   *   <li>Not already in cleanup_record table
   * </ul>
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<Resource> retrieveResourceToCleanup(int limit) {
    String sql =
        "select r.id, r.cloud_resource_uid, r.pool_id, r.state, r.request_handout_id, "
            + "r.creation, r.deletion, r.handout_time "
            + "FROM resource r "
            + "LEFT JOIN cleanup_record c ON r.id = c.resource_id "
            + "WHERE r.state = :state "
            + "AND c.resource_id IS NULL "
            + "LIMIT :limit";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", ResourceState.HANDED_OUT.toString())
            .addValue("limit", limit);

    return jdbcTemplate.query(sql, params, RESOURCE_ROW_MAPPER);
  }

  private static final RowMapper<Pool> POOL_ROW_MAPPER =
      (rs, rowNum) ->
          Pool.builder()
              .id(PoolId.create(rs.getString("id")))
              .resourceConfig(deserializeResourceConfig(rs.getString("resource_config")))
              .resourceType(ResourceType.valueOf(rs.getString("resource_type")))
              .status(PoolStatus.valueOf(rs.getString("status")))
              .size(rs.getInt("size"))
              .creation(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .build();

  private static final RowMapper<Resource> RESOURCE_ROW_MAPPER =
      (rs, rowNum) ->
          Resource.builder()
              .id(ResourceId.create(rs.getObject("id", UUID.class)))
              .poolId(PoolId.create(rs.getString("pool_id")))
              .cloudResourceUid(
                  rs.getString("cloud_resource_uid") == null
                      ? null
                      : deserializeResourceUid(rs.getString("cloud_resource_uid")))
              .state(ResourceState.valueOf(rs.getString("state")))
              .requestHandoutId(
                  rs.getString("request_handout_id") == null
                      ? null
                      : RequestHandoutId.create(rs.getString("request_handout_id")))
              .creation(rs.getObject("creation", OffsetDateTime.class).toInstant())
              .handoutTime(
                  rs.getString("handout_time") == null
                      ? null
                      : rs.getObject("handout_time", OffsetDateTime.class).toInstant())
              .deletion(
                  rs.getString("deletion") == null
                      ? null
                      : rs.getObject("deletion", OffsetDateTime.class).toInstant())
              .build();

  /**
   * A {@link ResultSetExtractor} for extracting the results of a join of the one pool to many
   * {@link ResourceState} relationship.
   */
  private static class PoolAndResourceStatesExtractor
      implements ResultSetExtractor<List<PoolAndResourceStates>> {
    @Override
    public List<PoolAndResourceStates> extractData(ResultSet rs)
        throws SQLException, DataAccessException {
      Map<PoolId, PoolAndResourceStates.Builder> pools = new HashMap<>();
      int rowNum = 0;
      while (rs.next()) {
        PoolId id = PoolId.create(rs.getString("id"));
        PoolAndResourceStates.Builder poolAndResourceStateBuilder = pools.get(id);
        if (poolAndResourceStateBuilder == null) {
          poolAndResourceStateBuilder = PoolAndResourceStates.builder();
          poolAndResourceStateBuilder.setPool(POOL_ROW_MAPPER.mapRow(rs, rowNum));
          pools.put(id, poolAndResourceStateBuilder);
        }
        if (rs.getString("state") != null) {
          // resourceState may be null from left join for a pool with no resources.
          poolAndResourceStateBuilder.setResourceStateCount(
              ResourceState.valueOf(rs.getString("state")), rs.getInt("resource_count"));
        }
        ++rowNum;
      }
      return pools.values().stream()
          .map(PoolAndResourceStates.Builder::build)
          .collect(Collectors.toList());
    }
  }

  /** Serializes {@link ResourceConfig} into json format string. */
  private static String serializeResourceConfig(ResourceConfig resourceConfig) {
    try {
      return new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .writeValueAsString(resourceConfig);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed to serialize ResourceConfig: %s", resourceConfig), e);
    }
  }

  /** Deserializes {@link ResourceConfig} into json format string. */
  private static ResourceConfig deserializeResourceConfig(String resourceConfig) {
    try {
      return new ObjectMapper().readValue(resourceConfig, ResourceConfig.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed to deserialize ResourceConfig: %s", resourceConfig), e);
    }
  }

  /** Serializes {@link CloudResourceUid} into json format string. */
  private static String serializeResourceUid(CloudResourceUid resourceUid) {
    try {
      return new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .writeValueAsString(resourceUid);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed to serialize ResourceConfig: %s", resourceUid), e);
    }
  }

  /** Deserializes {@link CloudResourceUid} into json format string. */
  private static CloudResourceUid deserializeResourceUid(String cloudResourceUid) {
    try {
      return new ObjectMapper().readValue(cloudResourceUid, CloudResourceUid.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          String.format("Failed to deserialize ResourceConfig: %s", cloudResourceUid), e);
    }
  }
}
