package bio.terra.buffer.app.controller;

import bio.terra.buffer.app.configuration.BufferJdbcConfiguration;
import bio.terra.buffer.generated.controller.UnauthenticatedApi;
import bio.terra.buffer.generated.model.SystemStatus;
import bio.terra.buffer.generated.model.SystemStatusSystems;
import bio.terra.common.stairway.StairwayLifecycleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final StairwayLifecycleManager stairwayLifecycleManager;

  @Autowired
  UnauthenticatedApiController(
      BufferJdbcConfiguration jdbcConfiguration,
      StairwayLifecycleManager StairwayLifecycleManager,
      PoolingDataSource<PoolableConnection> dataSource) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    this.stairwayLifecycleManager = StairwayLifecycleManager;
  }

  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
    SystemStatus systemStatus = new SystemStatus();

    final boolean postgresOk =
        jdbcTemplate.getJdbcTemplate().execute((Connection connection) -> connection.isValid(0));
    systemStatus.putSystemsItem("postgres", new SystemStatusSystems().ok(postgresOk));

    StairwayLifecycleManager.Status stairwayStatus = stairwayLifecycleManager.getStatus();
    final boolean stairwayOk = stairwayStatus.equals(StairwayLifecycleManager.Status.OK);
    systemStatus.putSystemsItem(
        "stairway",
        new SystemStatusSystems().ok(stairwayOk).addMessagesItem(stairwayStatus.toString()));

    systemStatus.ok(postgresOk && stairwayOk);
    if (systemStatus.isOk()) {
      return new ResponseEntity<>(systemStatus, HttpStatus.OK);
    } else {
      return new ResponseEntity<>(systemStatus, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /** Required if using Swagger-CodeGen, but actually we don't need this. */
  @Override
  public Optional<ObjectMapper> getObjectMapper() {
    return Optional.empty();
  }

  /** Required if using Swagger-CodeGen, but actually we don't need this. */
  @Override
  public Optional<HttpServletRequest> getRequest() {
    return Optional.empty();
  }
}
