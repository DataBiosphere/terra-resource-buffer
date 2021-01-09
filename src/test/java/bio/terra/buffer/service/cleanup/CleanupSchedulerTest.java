package bio.terra.buffer.service.cleanup;

import static bio.terra.buffer.app.configuration.CrlConfiguration.CLIENT_NAME;
import static bio.terra.buffer.app.configuration.CrlConfiguration.TEST_RESOURCE_TIME_TO_LIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bio.terra.buffer.app.configuration.CrlConfiguration;
import bio.terra.buffer.common.*;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.buffer.generated.model.GoogleProjectUid;
import bio.terra.buffer.generated.model.ResourceConfig;
import bio.terra.janitor.model.CreateResourceRequestBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableList;
import com.google.pubsub.v1.PubsubMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CleanupSchedulerTest extends BaseUnitTest {
  private static final Instant CREATION = Instant.now();

  @Mock private final Publisher mockPublisher = mock(Publisher.class);
  @Mock ApiFuture<String> mockMessageIdFuture = mock(ApiFuture.class);

  private final ArgumentCaptor<PubsubMessage> messageArgumentCaptor =
      ArgumentCaptor.forClass(PubsubMessage.class);

  @Autowired BufferDao bufferDao;

  private CrlConfiguration crlConfiguration = new CrlConfiguration();
  private CleanupScheduler cleanupScheduler;
  private ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  @BeforeEach
  public void setUp() throws Exception {
    crlConfiguration.setCleanupAfterHandout(true);
    // Those are all arbitrary values because we use mock, and not actually publish message.
    crlConfiguration.setJanitorClientCredentialFilePath("testPath");
    crlConfiguration.setJanitorTrackResourceProjectId("projectId");
    crlConfiguration.setJanitorTrackResourceTopicId("topicId");
    cleanupScheduler =
        new CleanupScheduler(bufferDao, crlConfiguration, Clock.fixed(CREATION, ZoneId.of("UTC")));
    cleanupScheduler.providePublisher(mockPublisher);
    when(mockPublisher.publish(any())).thenReturn(mockMessageIdFuture);
    when(mockMessageIdFuture.get()).thenReturn("message");
  }

  @AfterEach
  public void tearDown() {
    // Shutdown the CleanupScheduler so that it isn't running during other tests.
    cleanupScheduler.shutdown();
  }

  @Test
  public void testScheduleCleanup() throws Exception {
    Pool pool =
        Pool.builder()
            .creation(CREATION)
            .id(PoolId.create("poolId"))
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(new ResourceConfig())
            .status(PoolStatus.ACTIVE)
            .build();

    CloudResourceUid cloudResourceUid =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p1"));
    Resource resource =
        Resource.builder()
            .id(ResourceId.create(UUID.randomUUID()))
            .poolId(pool.id())
            .creation(Instant.now())
            .state(ResourceState.CREATING)
            .build();
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(resource);
    bufferDao.updateResourceAsReady(resource.id(), cloudResourceUid);
    bufferDao.updateOneReadyResourceToHandedOut(pool.id(), RequestHandoutId.create("1111"));
    assertEquals(1, bufferDao.retrieveResourceToCleanup(10).size());

    cleanupScheduler.initialize();
    Thread.sleep(1000);

    verify(mockPublisher).publish(messageArgumentCaptor.capture());

    assertThat(
        messageArgumentCaptor.getAllValues().stream()
            .map(m -> m.getData().toStringUtf8())
            .collect(Collectors.toList()),
        Matchers.containsInAnyOrder(
            objectMapper.writeValueAsString(
                new CreateResourceRequestBody()
                    .creation(CREATION.atOffset(ZoneOffset.UTC))
                    .expiration(CREATION.plus(TEST_RESOURCE_TIME_TO_LIVE).atOffset(ZoneOffset.UTC))
                    .putLabelsItem("client", CLIENT_NAME)
                    .resourceUid(
                        new bio.terra.janitor.model.CloudResourceUid()
                            .googleProjectUid(
                                new bio.terra.janitor.model.GoogleProjectUid().projectId("p1"))))));
    assertTrue(bufferDao.retrieveResourceToCleanup(10).isEmpty());
  }
}
