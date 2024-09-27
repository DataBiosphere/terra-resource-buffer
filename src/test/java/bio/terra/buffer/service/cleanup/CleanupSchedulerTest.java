package bio.terra.buffer.service.cleanup;

import static bio.terra.buffer.app.configuration.CrlConfiguration.CLIENT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.buffer.app.configuration.CrlConfiguration;
import bio.terra.buffer.common.BaseUnitTest;
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
import bio.terra.buffer.generated.model.GcpProjectConfig;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
  private static final Instant CREATION = BufferDao.currentInstant();

  @Mock private final Publisher mockPublisher = mock(Publisher.class);
  @Mock ApiFuture<String> mockMessageIdFuture = mock(ApiFuture.class);

  private final ArgumentCaptor<PubsubMessage> messageArgumentCaptor =
      ArgumentCaptor.forClass(PubsubMessage.class);

  @Autowired BufferDao bufferDao;

  private CrlConfiguration crlConfiguration = new CrlConfiguration();
  private JanitorResourceCleanupScheduler cleanupScheduler;
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
    crlConfiguration.setTestResourceTimeToLive(Duration.ofHours(10));
    cleanupScheduler =
        new JanitorResourceCleanupScheduler(
            bufferDao, crlConfiguration, Clock.fixed(CREATION, ZoneId.of("UTC")));
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
            .creation(BufferDao.currentInstant())
            .state(ResourceState.CREATING)
            .build();
    bufferDao.createPools(ImmutableList.of(pool));
    bufferDao.createResource(resource);
    bufferDao.updateResourceAsReady(resource.id(), cloudResourceUid);
    bufferDao.updateOneReadyResourceToHandedOut(pool.id(), RequestHandoutId.create("1111"));
    assertEquals(1, bufferDao.retrieveResourceToCleanup(10, true).size());

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
                    .expiration(
                        CREATION
                            .plus(crlConfiguration.getTestResourceTimeToLive())
                            .atOffset(ZoneOffset.UTC))
                    .putLabelsItem("client", CLIENT_NAME)
                    .resourceUid(
                        new bio.terra.janitor.model.CloudResourceUid()
                            .googleProjectUid(
                                new bio.terra.janitor.model.GoogleProjectUid().projectId("p1"))))));
    assertTrue(bufferDao.retrieveResourceToCleanup(10, true).isEmpty());
  }

  @Test
  public void testAutoDelete() throws Exception {
    // For this test, do not use the global cleanupAfterHandout value. This will be reset in the
    // setup code, so this does not interfere with other tests.
    crlConfiguration.setCleanupAfterHandout(false);
    GcpProjectConfig autoDeleteConfig = new GcpProjectConfig().autoDelete(true);
    ResourceConfig resourceConfig = new ResourceConfig().gcpProjectConfig(autoDeleteConfig);
    Pool autoDeletePool =
        Pool.builder()
            .creation(CREATION)
            .id(PoolId.create("autoDeletePool"))
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(resourceConfig)
            .status(PoolStatus.ACTIVE)
            .build();
    Pool noAutoDeletePool =
        Pool.builder()
            .creation(CREATION)
            .id(PoolId.create("noAutoDeletePool"))
            .resourceType(ResourceType.GOOGLE_PROJECT)
            .size(1)
            .resourceConfig(new ResourceConfig())
            .status(PoolStatus.ACTIVE)
            .build();
    bufferDao.createPools(List.of(autoDeletePool, noAutoDeletePool));

    CloudResourceUid autoDeleteProject =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p1"));
    Resource resource1 =
        Resource.builder()
            .id(ResourceId.create(UUID.randomUUID()))
            .poolId(autoDeletePool.id())
            .creation(BufferDao.currentInstant())
            .state(ResourceState.CREATING)
            .build();
    bufferDao.createResource(resource1);
    bufferDao.updateResourceAsReady(resource1.id(), autoDeleteProject);
    bufferDao.updateOneReadyResourceToHandedOut(
        autoDeletePool.id(), RequestHandoutId.create("1111"));

    CloudResourceUid noAutoDeleteProject =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p2"));
    Resource resource2 =
        Resource.builder()
            .id(ResourceId.create(UUID.randomUUID()))
            .poolId(noAutoDeletePool.id())
            .creation(BufferDao.currentInstant())
            .state(ResourceState.CREATING)
            .build();
    bufferDao.createResource(resource2);
    bufferDao.updateResourceAsReady(resource2.id(), noAutoDeleteProject);
    bufferDao.updateOneReadyResourceToHandedOut(
        noAutoDeletePool.id(), RequestHandoutId.create("1111"));

    // Verify that the auto-delete project was marked for cleanup, but the project from the other
    // pool was not.
    cleanupScheduler.initialize();
    Thread.sleep(1000);

    verify(mockPublisher).publish(messageArgumentCaptor.capture());

    List<String> capturedMessages =
        messageArgumentCaptor.getAllValues().stream()
            .map(m -> m.getData().toStringUtf8())
            .collect(Collectors.toList());
    assertEquals(1, capturedMessages.size());
    assertThat(
        capturedMessages,
        Matchers.containsInAnyOrder(
            objectMapper.writeValueAsString(
                new CreateResourceRequestBody()
                    .creation(CREATION.atOffset(ZoneOffset.UTC))
                    .expiration(
                        CREATION
                            .plus(crlConfiguration.getTestResourceTimeToLive())
                            .atOffset(ZoneOffset.UTC))
                    .putLabelsItem("client", CLIENT_NAME)
                    .resourceUid(
                        new bio.terra.janitor.model.CloudResourceUid()
                            .googleProjectUid(
                                new bio.terra.janitor.model.GoogleProjectUid().projectId("p1"))))));
    // cleanupAllPools must be false here as cleanupAfterHandout is also false.
    assertTrue(bufferDao.retrieveResourceToCleanup(10, false).isEmpty());
  }
}
