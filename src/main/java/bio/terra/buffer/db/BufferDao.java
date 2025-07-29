package bio.terra.buffer.db;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_JDBC_TEMPLATE;
import static bio.terra.buffer.app.configuration.BeanNames.OBJECT_MAPPER;

import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.PoolAndResourceStates;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.RequestHandoutId;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.service.resource.flight.CreateNetworkStep;
import bio.terra.common.exception.InternalServerErrorException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
      @Qualifier(BUFFER_JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbcTemplate,
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
  // TODO: consider delaying expiration so pool can be recovered
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

  /**
   * Set Pools' status back to active and clear expiration column to re-activate the pools. The size
   * and other attributes are left untouched; it would complicate matters to support re-activation
   * and size changes in one shot.
   *
   * @param pools - Pool objects with size populated. Note that other attributes will be ignored as
   *     changing them is not yet supported.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void reactivatePools(List<Pool> pools) {
    String sql =
        "UPDATE pool SET status = :status, expiration = :expiration, size = :size"
            + " WHERE id = :id ";
    MapSqlParameterSource[] sqlParameterSourceList =
        pools.stream()
            .map(
                pool ->
                    new MapSqlParameterSource()
                        .addValue("id", pool.id().toString())
                        .addValue("status", PoolStatus.ACTIVE.toString())
                        .addValue("expiration", null)
                        .addValue("size", pool.size()))
            .toArray(MapSqlParameterSource[]::new);

    jdbcTemplate.batchUpdate(sql, sqlParameterSourceList);
  }

  /** Updates list of pools' size. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void updatePoolsSizes(Map<PoolId, Integer> poolsToUpdateSize) {
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

  /** Retrieve a resource by id. */
  @Transactional(propagation = Propagation.SUPPORTS)
  public Optional<Resource> retrieveResource(CloudResourceUid cloudResourceUid) {
    String sql =
            "select id, pool_id, creation, handout_time, state, request_handout_id, cloud_resource_uid, deletion "
                    + "FROM resource "
                    + "WHERE cloud_resource_uid = :cloud_resource_uid::jsonb";

    MapSqlParameterSource params = new MapSqlParameterSource().addValue("cloud_resource_uid", serializeResourceUid(cloudResourceUid));

    return Optional.ofNullable(
            DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, RESOURCE_ROW_MAPPER)));
  }

  /** Randomly retrieve resources match the {@link ResourceState}. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public List<Resource> retrieveResourcesRandomly(PoolId poolId, ResourceState state, int limit) {
    String sql =
        "select id, pool_id, creation, handout_time, state, request_handout_id, cloud_resource_uid, deletion "
            + "FROM resource "
            + "WHERE state = :state AND pool_id = :pool_id "
            + "ORDER BY random() LIMIT :limit";

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

  @VisibleForTesting
  @Transactional
  public void updateResourceHandoutTime(ResourceId id, Instant handoutTime) {
    String sql = "UPDATE resource SET handout_time = :handout_time WHERE id = :id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("handout_time", handoutTime.atOffset(ZoneOffset.UTC))
            .addValue("id", id.id());
    jdbcTemplate.update(sql, params);
  }

  /**
   * Pick one READY resource and handed it out to client. The steps are:
   *
   * <ul>
   *   <li>Step 1: Checks if any resource uses this {@link RequestHandoutId}, if yes, return the
   *       resource.
   *   <li>Step 2: Randomly pick a READY entity from resource table
   *   <li>Step 3: Update this resource state to HANDED_OUT
   *   <li>Step 4: Return step 2's resource.
   * </ul>
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public Optional<Resource> updateOneReadyResourceToHandedOut(
      PoolId poolId, RequestHandoutId requestHandoutId) {
    Optional<Resource> existingResource = retrieveResource(poolId, requestHandoutId);
    if (existingResource.isPresent()) {
      if (existingResource.get().state().equals(ResourceState.HANDED_OUT)) {
        logger.info(
            "Resource {}, requestHandoutId {} already handed out. Handing out again...",
            existingResource.get().id(),
            requestHandoutId);
        return existingResource;
      } else {
        // Should never happens but we want to double check to make sure we don't handout 'bad'
        // resource.
        throw new InternalServerErrorException(
            String.format(
                "Unexpected handed out resource state found in pool id: %s, requestHandoutId: %s",
                poolId, requestHandoutId));
      }
    } else {
      List<Resource> resources = retrieveResourcesRandomly(poolId, ResourceState.READY, 1);
      if (resources.size() == 0) {
        logger.warn("No resource is ready to use at this moment for pool: {}.", poolId);
        return Optional.empty();
      } else {
        Resource selectedResource = resources.get(0);
        String sql =
            "UPDATE resource "
                + "SET state = :state, request_handout_id = :request_handout_id, handout_time = :handout_time"
                + " WHERE id = :id AND state = :previous_state AND request_handout_id IS null";

        MapSqlParameterSource params =
            new MapSqlParameterSource()
                .addValue("state", ResourceState.HANDED_OUT.toString())
                .addValue("previous_state", ResourceState.READY.toString())
                .addValue("request_handout_id", requestHandoutId.id())
                .addValue("handout_time", OffsetDateTime.now(ZoneOffset.UTC))
                .addValue("id", selectedResource.id().id());

        // Return the selectedResource if update successfully. Otherwise, return empty.
        return jdbcTemplate.update(sql, params) == 1
            ? Optional.of(selectedResource)
            : Optional.empty();
      }
    }
  }

  /**
   * Updates resource in READY state to DELETING. Returns true if previous state is READY and we
   * successfully update its state.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean updateReadyResourceToDeleting(ResourceId id) {
    Optional<Resource> resource = retrieveResource(id);
    if (resource.isEmpty() || !resource.get().state().equals(ResourceState.READY)) {
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
   * Resource Buffer Service publishes this resource message to Janitor. This is only expected to be
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
   * Inserts a record into cleanup_record table with creation timestamp. This is a convenience
   * method for testing
   */
  @VisibleForTesting
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void insertCleanupRecordWithCreationTimestamp(ResourceId resourceId, Instant creation) {
    String sql =
        "INSERT INTO cleanup_record (resource_id, created_at) values (:resource_id, :created_at)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("resource_id", resourceId.id())
            .addValue("created_at", OffsetDateTime.ofInstant(creation, ZoneOffset.UTC));

    jdbcTemplate.update(sql, params);
  }

  /**
   * Retrieves resources that need cleanup by Janitor. Those resources should be:
   *
   * <ul>
   *   <li>State is HANDED_OUT in resource table
   *   <li>Not already in cleanup_record table
   * </ul>
   *
   * @param limit The maximum number of resources to return
   * @param cleanupAllPools If true, select resources from all pools regardless of the resource
   *     config's autoDelete value.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<Resource> retrieveResourceToCleanup(int limit, boolean cleanupAllPools) {
    StringBuilder sqlBuilder =
        new StringBuilder(
            "select r.id, r.cloud_resource_uid, r.pool_id, r.state, r.request_handout_id, "
                + "r.creation, r.deletion, r.handout_time "
                + "FROM resource r "
                + "LEFT JOIN cleanup_record c ON r.id = c.resource_id "
                + "LEFT JOIN pool p ON r.pool_id = p.id "
                + "WHERE r.state = :state "
                + "AND c.resource_id IS NULL ");
    if (!cleanupAllPools) {
      // The 'autoDelete' field is deserialized as text, not a boolean.
      sqlBuilder.append(
          "AND p.resource_config::jsonb->'gcpProjectConfig'->>'autoDelete' = 'true' ");
    }
    sqlBuilder.append("LIMIT :limit");

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", ResourceState.HANDED_OUT.toString())
            .addValue("limit", limit);

    return jdbcTemplate.query(sqlBuilder.toString(), params, RESOURCE_ROW_MAPPER);
  }

  /**
   * Given a retention period, remove dead resource records that are either: 1) handed out and older
   * than the retention period, or 2) marked as deleted
   *
   * @param retentionDays retention period in days for handed out resource records before they are
   *     eligible for deletion
   * @param batchSize number of records to delete in one batch
   * @return number of records deleted
   */
  @Transactional
  public int removeDeadResourceRecords(int retentionDays, int batchSize) {
    String sql =
        "DELETE FROM resource WHERE id IN ("
            + "SELECT id FROM resource "
            + "WHERE state IN (:handedOutState, :deletedState) AND "
            + " ("
            + "     (state = :handedOutState AND handout_time < NOW() - MAKE_INTERVAL(days => :retentionDays)) OR "
            + "     (state = :deletedState) "
            + " ) "
            + "LIMIT :batchSize "
            + ")";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("handedOutState", ResourceState.HANDED_OUT.toString())
            .addValue("deletedState", ResourceState.DELETED.toString())
            .addValue("retentionDays", retentionDays)
            .addValue("batchSize", batchSize);

    return jdbcTemplate.update(sql, params);
  }

  /**
   * Given a retention period, remove dead cleanup records that are older than the retention period.
   *
   * @param retentionDays retention period in days for cleanup records before they are eligible for
   *     deletion
   * @param batchSize number of records to delete in one batch
   * @return number of records deleted
   */
  @Transactional
  public int removeDeadCleanupRecords(int retentionDays, int batchSize) {
    String sql =
        "DELETE FROM cleanup_record WHERE resource_id IN ("
            + "SELECT resource_id FROM cleanup_record "
            + "WHERE created_at < NOW() - MAKE_INTERVAL(days => :retentionDays) "
            + "LIMIT :batchSize "
            + ")";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("retentionDays", retentionDays)
            .addValue("batchSize", batchSize);

    return jdbcTemplate.update(sql, params);
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
      Map<PoolId, PoolAndResourceStates.Builder> poolIdToBuilder = new HashMap<>();
      int rowNum = 0;
      while (rs.next()) {
        PoolId id = PoolId.create(rs.getString("id"));
        PoolAndResourceStates.Builder poolAndResourceStateBuilder = poolIdToBuilder.get(id);
        if (poolAndResourceStateBuilder == null) {
          poolAndResourceStateBuilder = PoolAndResourceStates.builder();
          poolAndResourceStateBuilder.setPool(POOL_ROW_MAPPER.mapRow(rs, rowNum));
          poolIdToBuilder.put(id, poolAndResourceStateBuilder);
        }
        if (rs.getString("state") != null) {
          // resourceState may be null from left join for a pool with no resources.
          poolAndResourceStateBuilder.setResourceStateCount(
              ResourceState.valueOf(rs.getString("state")), rs.getInt("resource_count"));
        }
        ++rowNum;
      }
      return poolIdToBuilder.values().stream()
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

  // TODO(PF-1912) move to terra-common-lib
  /** Helper function to return Millisecond precision instant supported by most DBs */
  public static Instant currentInstant() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }
}
