package bio.terra.rbs.service.cleanup;

import static bio.terra.rbs.app.configuration.CrlConfiguration.CLIENT_NAME;
import static bio.terra.rbs.app.configuration.CrlConfiguration.TEST_RESOURCE_TIME_TO_LIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bio.terra.janitor.model.CreateResourceRequestBody;
import bio.terra.rbs.app.configuration.CrlConfiguration;
import bio.terra.rbs.common.*;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GoogleProjectUid;
import bio.terra.rbs.generated.model.ResourceConfig;
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

  @Autowired RbsDao rbsDao;

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
        new CleanupScheduler(rbsDao, crlConfiguration, Clock.fixed(CREATION, ZoneId.of("UTC")));
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
    rbsDao.createPools(ImmutableList.of(pool));
    rbsDao.createResource(resource);
    rbsDao.updateResourceAsReady(resource.id(), cloudResourceUid);
    rbsDao.updateResourceAsHandedOut(resource.id(), RequestHandoutId.create("1111"));
    assertEquals(1, rbsDao.retrieveResourceToCleanup(10).size());

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
    assertTrue(rbsDao.retrieveResourceToCleanup(10).isEmpty());
  }
}
