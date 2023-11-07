package bio.terra.buffer.app.controller;

import bio.terra.buffer.common.MetricsHelper;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.RequestHandoutId;
import bio.terra.buffer.generated.controller.BufferApi;
import bio.terra.buffer.generated.model.HandoutRequestBody;
import bio.terra.buffer.generated.model.PoolInfo;
import bio.terra.buffer.generated.model.ResourceInfo;
import bio.terra.buffer.service.pool.PoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class BufferApiController implements BufferApi {
  private final PoolService poolService;
  private final MetricsHelper metricsHelper;

  @Autowired
  BufferApiController(PoolService poolService, MetricsHelper metricsHelper) {
    this.poolService = poolService;
    this.metricsHelper = metricsHelper;
  }

  @Override
  public ResponseEntity<ResourceInfo> handoutResource(
      String poolId, HandoutRequestBody handoutRequestBody) {
    metricsHelper.recordHandoutResourceRequest(PoolId.create(poolId));
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
}
