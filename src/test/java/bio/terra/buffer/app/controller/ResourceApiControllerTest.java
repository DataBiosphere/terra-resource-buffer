package bio.terra.buffer.app.controller;

import bio.terra.buffer.app.Main;
import bio.terra.buffer.common.*;
import bio.terra.buffer.common.PoolStatus;
import bio.terra.buffer.common.exception.NotFoundException;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.*;
import bio.terra.buffer.service.job.JobService;
import bio.terra.buffer.service.resource.FlightScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@ActiveProfiles({"test", "unit"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class, properties = "terra.common.prometheus.endpointEnabled=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ResourceApiControllerTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private BufferDao mockBufferDAO;
  @MockitoBean private JobService mockJobService;
  @MockitoBean private FlightScheduler mockFlightScheduler;
    @Autowired
    private BufferDao bufferDao;

  @Test
  public void repairResource_ok() throws Exception {
    String projectId = "test-project-id";
    GoogleProjectUid googleProjectUid = new GoogleProjectUid().projectId(projectId);
    CloudResourceUid cloudResourceUid = new CloudResourceUid().googleProjectUid(googleProjectUid);

    Pool mockPool = Pool.builder()
            .id(PoolId.create("test-pool"))
            .size(10)
            .resourceConfig(new ResourceConfig()) // Use appropriate resource config
            .creation(BufferDao.currentInstant())
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .status(PoolStatus.ACTIVE)
            .build();

    Resource mockResource = Resource.builder()
            .id(ResourceId.create(UUID.randomUUID()))
            .poolId(PoolId.create("test-pool"))
            .cloudResourceUid(cloudResourceUid)
            .creation(BufferDao.currentInstant())
            .state(ResourceState.HANDED_OUT)
            .requestHandoutId(RequestHandoutId.create("test-handout-id"))
            .build();

    Mockito.when(bufferDao.retrieveResource(cloudResourceUid))
            .thenReturn(Optional.of(mockResource));
    Mockito.when(mockBufferDAO.retrievePool(mockResource.poolId()))
            .thenReturn(Optional.of(mockPool));
    Mockito.when(mockFlightScheduler.submitRepairResourceFlight(mockPool, googleProjectUid))
            .thenReturn(Optional.of("flight-123"));

    JobModel mockJobModel = new JobModel()
            .id("flight-123")
            .className("RepairResourceFlight")
            .description("Repair resource for project " + projectId)
            .jobStatus(JobModel.JobStatusEnum.RUNNING)
            .statusCode(202);

    Mockito.when(mockJobService.retrieveJob("flight-123")).thenReturn(mockJobModel);

    String response = this.mvc
            .perform(
                    post("/api/resource/v1/" + projectId + "/repair")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")) // no request body is needed for this endpoint
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JobModel actualJob = objectMapper.readValue(response, JobModel.class);
    assertEquals("flight-123", actualJob.getId());
    assertEquals("RepairResourceFlight", actualJob.getClassName());
    assertEquals(JobModel.JobStatusEnum.RUNNING, actualJob.getJobStatus());
  }

  @Test
  public void repairResourceProjectNotFound() throws Exception {
    String projectId = "fake-project-id";
    CloudResourceUid cloudResourceUid = new CloudResourceUid().googleProjectUid(
            new GoogleProjectUid().projectId(projectId));
    Mockito.when(mockBufferDAO.retrieveResource(cloudResourceUid))
            .thenThrow(new NotFoundException(String.format("Resource id does not exist: %s.", projectId)));
    this.mvc
            .perform(
                    post("/api/resource/v1/" + projectId + "/repair")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
            .andExpect(status().isNotFound());
  }

  @Test
  public void repairResourcePoolNotFound() throws Exception {
    String projectId = "project-without-pool";
    GoogleProjectUid googleProjectUid = new GoogleProjectUid().projectId(projectId);
    CloudResourceUid cloudResourceUid = new CloudResourceUid().googleProjectUid(googleProjectUid);
    Resource resource = Resource.builder()
            .id(ResourceId.create(UUID.randomUUID()))
            .poolId(PoolId.create("non-existent-pool"))
            .cloudResourceUid(cloudResourceUid)
            .creation(BufferDao.currentInstant())
            .state(ResourceState.HANDED_OUT)
            .requestHandoutId(RequestHandoutId.create("test-handout-id"))
            .build();
    Mockito.when(bufferDao.retrieveResource(cloudResourceUid))
            .thenReturn(Optional.of(resource));
    Mockito.when(mockBufferDAO.retrievePool(resource.poolId()))
            .thenThrow(new NotFoundException(String.format("Pool for this resource does not exist: %s.", resource.poolId())));
    this.mvc
            .perform(
                    post("/api/resource/v1/" + projectId + "/repair")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
            .andExpect(status().isNotFound());
  }
}
