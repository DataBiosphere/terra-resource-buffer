package bio.terra.buffer.app.controller;

import static bio.terra.buffer.common.MetricsHelper.recordHandoutResourceRequest;

import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.RequestHandoutId;
import bio.terra.buffer.generated.controller.BufferApi;
import bio.terra.buffer.generated.model.HandoutRequestBody;
import bio.terra.buffer.generated.model.PoolInfo;
import bio.terra.buffer.generated.model.ResourceInfo;
import bio.terra.buffer.service.pool.PoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class BufferApiController implements BufferApi {
  private final PoolService poolService;

  @Autowired
  BufferApiController(PoolService poolService) {
    this.poolService = poolService;
  }

  @Override
  public ResponseEntity<ResourceInfo> handoutResource(
      String poolId, HandoutRequestBody handoutRequestBody) {
    recordHandoutResourceRequest(PoolId.create(poolId));
    return new ResponseEntity<>(
        poolService.handoutResource(
            PoolId.create(poolId),
            RequestHandoutId.create(handoutRequestBody.getHandoutRequestId())),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<PoolInfo> getPoolInfo(String poolId) {
    return new ResponseEntity<>(poolService.getPoolInfo(PoolId.create(poolId)), HttpStatus.OK);
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
