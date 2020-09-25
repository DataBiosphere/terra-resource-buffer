package bio.terra.rbs.app.controller;

import bio.terra.rbs.generated.controller.UnauthenticatedApi;
import bio.terra.rbs.generated.model.SystemStatus;
import bio.terra.rbs.generated.model.SystemStatusSystems;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class UnauthenticatedApiController implements UnauthenticatedApi {
  private int statusCount;

  @Override
  public ResponseEntity<SystemStatus> serviceStatus() {
    // TODO: rbs: Replace this
    statusCount++;
    boolean status = (statusCount % 2 == 1);
    String reliable = "reliable";
    HttpStatus httpStatus = HttpStatus.OK;

    if (!status) {
      reliable = "unreliable";
      httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    SystemStatusSystems otherSystemStatus =
        new SystemStatusSystems().ok(status).addMessagesItem("other systems are SO " + reliable);

    SystemStatus systemStatus =
        new SystemStatus().ok(status).putSystemsItem("otherSystem", otherSystemStatus);

    return new ResponseEntity<>(systemStatus, httpStatus);
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
