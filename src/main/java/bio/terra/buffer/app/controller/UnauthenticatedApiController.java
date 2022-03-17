package bio.terra.buffer.app.controller;

import bio.terra.buffer.app.configuration.BufferDatabaseConfiguration;
import bio.terra.buffer.generated.controller.UnauthenticatedApi;
import bio.terra.buffer.generated.model.SystemStatus;
import bio.terra.buffer.generated.model.SystemStatusSystems;
import bio.terra.common.stairway.StairwayComponent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final StairwayComponent stairwayComponent;

  @Autowired
  UnauthenticatedApiController(
      BufferDatabaseConfiguration jdbcConfiguration, StairwayComponent stairwayComponent) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
    this.stairwayComponent = stairwayComponent;
  }

  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
    SystemStatus systemStatus = new SystemStatus();

    final boolean postgresOk =
        jdbcTemplate.getJdbcTemplate().execute((Connection connection) -> connection.isValid(0));
    systemStatus.putSystemsItem("postgres", new SystemStatusSystems().ok(postgresOk));

    StairwayComponent.Status stairwayStatus = stairwayComponent.getStatus();
    final boolean stairwayOk = stairwayStatus.equals(StairwayComponent.Status.OK);
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
}
