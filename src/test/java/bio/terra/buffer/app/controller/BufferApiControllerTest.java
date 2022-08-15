package bio.terra.buffer.app.controller;

import bio.terra.buffer.app.Main;
import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.PoolId;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.RequestHandoutId;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceId;
import bio.terra.buffer.common.ResourceState;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.generated.model.HandoutRequestBody;
import bio.terra.buffer.generated.model.PoolConfig;
import bio.terra.buffer.generated.model.PoolInfo;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.generated.model.ResourceInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static bio.terra.buffer.common.MetricsHelper.HANDOUT_RESOURCE_REQUEST_COUNT_VIEW;
import static bio.terra.buffer.common.testing.MetricsTestUtil.assertCountIncremented;
import static bio.terra.buffer.common.testing.MetricsTestUtil.getCurrentCount;
import static bio.terra.buffer.common.testing.MetricsTestUtil.getPoolIdTag;
import static bio.terra.buffer.common.testing.MetricsTestUtil.sleepForSpansExport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@ActiveProfiles({"test", "unit"})
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class BufferApiControllerTest {
  @Autowired private MockMvc mvc;
  @Autowired private BufferDao bufferDao;
  @Autowired private ObjectMapper objectMapper;

  @Test
  public void handoutResource_ok() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    long currentCount =
        getCurrentCount(HANDOUT_RESOURCE_REQUEST_COUNT_VIEW.getName(), getPoolIdTag(poolId));
    RequestHandoutId requestHandoutId = RequestHandoutId.create("requestHandoutId");
    CloudResourceUid cloudResourceUid =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("projectId"));
    bufferDao.createPools(
        ImmutableList.of(
            Pool.builder()
                .creation(BufferDao.currentInstant())
                .id(poolId)
                .resourceType(ResourceType.GOOGLE_PROJECT)
                .size(1)
                .resourceConfig(new ResourceConfig().configName("resourceName"))
                .status(PoolStatus.ACTIVE)
                .build()));
    ResourceId resourceId = ResourceId.create(UUID.randomUUID());
    bufferDao.createResource(
        Resource.builder()
            .id(resourceId)
            .poolId(poolId)
            .creation(BufferDao.currentInstant())
            .state(ResourceState.CREATING)
            .build());
    bufferDao.updateResourceAsReady(resourceId, cloudResourceUid);

    String response =
        this.mvc
            .perform(
                put("/api/pool/v1/" + poolId.id() + "/resource")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new HandoutRequestBody().handoutRequestId(requestHandoutId.id()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ResourceInfo resourceInfo = objectMapper.readValue(response, ResourceInfo.class);
    assertEquals(
        new ResourceInfo()
            .poolId(poolId.id())
            .cloudResourceUid(cloudResourceUid)
            .requestHandoutId(requestHandoutId.id()),
        resourceInfo);

    // Verify the metric record this event twice
    sleepForSpansExport();
    assertCountIncremented(
        HANDOUT_RESOURCE_REQUEST_COUNT_VIEW.getName(), getPoolIdTag(poolId), currentCount, 1);
  }

  @Test
  public void handoutResource_noResource() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    bufferDao.createPools(
        ImmutableList.of(
            Pool.builder()
                .creation(BufferDao.currentInstant())
                .id(poolId)
                .resourceType(ResourceType.GOOGLE_PROJECT)
                .size(1)
                .resourceConfig(new ResourceConfig().configName("resourceName"))
                .status(PoolStatus.ACTIVE)
                .build()));

    this.mvc
        .perform(
            put("/api/pool/v1/poolId/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new HandoutRequestBody().handoutRequestId("handoutRequestId"))))
        .andExpect(status().isNotFound());
  }

  @Test
  public void handoutResource_invalidPoolId() throws Exception {
    this.mvc
        .perform(
            put("/api/pool/v1/poolId/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new HandoutRequestBody().handoutRequestId("handoutRequestId"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void getPoolInfo_ok() throws Exception {
    PoolId poolId = PoolId.create("poolId");
    bufferDao.createPools(
        ImmutableList.of(
            Pool.builder()
                .creation(BufferDao.currentInstant())
                .id(poolId)
                .resourceType(ResourceType.GOOGLE_PROJECT)
                .size(2)
                .resourceConfig(new ResourceConfig().configName("resourceName"))
                .status(PoolStatus.ACTIVE)
                .build()));
    ResourceId resourceId1 = ResourceId.create(UUID.randomUUID());
    ResourceId resourceId2 = ResourceId.create(UUID.randomUUID());

    bufferDao.createResource(
        Resource.builder()
            .id(resourceId1)
            .poolId(poolId)
            .creation(BufferDao.currentInstant())
            .state(ResourceState.CREATING)
            .build());
    bufferDao.createResource(
        Resource.builder()
            .id(resourceId2)
            .poolId(poolId)
            .creation(BufferDao.currentInstant())
            .state(ResourceState.CREATING)
            .build());
    bufferDao.updateResourceAsReady(
        resourceId1,
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("projectId")));

    String response =
        this.mvc
            .perform(get("/api/pool/v1/" + poolId.id()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    PoolInfo poolInfo = objectMapper.readValue(response, PoolInfo.class);
    assertEquals(
        new PoolInfo()
            .putResourceStateCountItem(ResourceState.READY.name(), 1)
            .putResourceStateCountItem(ResourceState.CREATING.name(), 1)
            .putResourceStateCountItem(ResourceState.DELETED.name(), 0)
            .putResourceStateCountItem(ResourceState.HANDED_OUT.name(), 0)
            .status(bio.terra.buffer.generated.model.PoolStatus.ACTIVE)
            .poolConfig(
                new PoolConfig()
                    .poolId(poolId.toString())
                    .size(2)
                    .resourceConfigName("resourceName")),
        poolInfo);
  }

  @Test
  public void getPoolInfo() throws Exception {
    this.mvc.perform(get("/api/pool/v1/poolId")).andExpect(status().isNotFound());
  }
}
