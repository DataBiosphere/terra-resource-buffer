package bio.terra.buffer.service.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bio.terra.buffer.common.*;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.buffer.service.stairway.StairwayComponent;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.StairwayExecutionException;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FlightManagerTest extends BaseUnitTest {
  @Mock private final StairwayComponent mockStairwayComponent = mock(StairwayComponent.class);
  @Mock private final Stairway mockStairway = mock(Stairway.class);

  @Autowired private BufferDao bufferDao;
  @Autowired private FlightSubmissionFactory flightSubmissionFactory;
  @Autowired private TransactionTemplate transactionTemplate;

  private FlightManager flightManager;

  @BeforeEach
  public void setup() throws Exception {
    when(mockStairwayComponent.get()).thenReturn(mockStairway);
    when(mockStairway.createFlightId()).thenReturn("flightId");
    doThrow(new StairwayExecutionException("test"))
        .when(mockStairway)
        .submitToQueue(anyString(), any(), any());

    flightManager =
        new FlightManager(
            bufferDao, flightSubmissionFactory, mockStairwayComponent, transactionTemplate);
  }

  @Test
  public void submitStairwayFail_rollbackResourceFromDB() throws Exception {
    Pool pool =
        Pool.builder()
            .creation(Instant.now())
            .id(PoolId.create(UUID.randomUUID().toString()))
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(10)
            .resourceConfig(new ResourceConfig().configName("resourceName"))
            .status(PoolStatus.ACTIVE)
            .build();
    bufferDao.createPools(ImmutableList.of(pool));

    assertFalse(flightManager.submitCreationFlight(pool).isPresent());
    // Resource is delete from database.
    assertTrue(bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.CREATING, 1).isEmpty());
  }
}
