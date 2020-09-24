package bio.terra.rbs.app.controller;

import bio.terra.rbs.generated.controller.RbsApi;
import bio.terra.rbs.service.ping.PingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RbsApiController implements RbsApi {
  private PingService pingService;

  @Autowired
  public RbsApiController(PingService pingService) {
    this.pingService = pingService;
  }

  @Override
  public ResponseEntity<String> ping(
      @RequestParam(value = "message", required = false) String message) {
    String result = pingService.computePing(message);
    return new ResponseEntity<>(result, HttpStatus.OK);
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
