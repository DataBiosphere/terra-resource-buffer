package bio.terra.rbs.app.controller;

import bio.terra.rbs.app.configuration.RbsJdbcConfiguration;
import bio.terra.rbs.generated.controller.RbsApi;
import bio.terra.rbs.generated.model.ResourceInfo;
import bio.terra.rbs.service.stairway.StairwayComponent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class RbsApiController implements RbsApi {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final StairwayComponent stairwayComponent;

  @Autowired
  RbsApiController(RbsJdbcConfiguration jdbcConfiguration, StairwayComponent stairwayComponent) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
    this.stairwayComponent = stairwayComponent;
  }

  @Override
  public ResponseEntity<ResourceInfo> handoutResource(String poolId, String handoutRequestId) {}

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
