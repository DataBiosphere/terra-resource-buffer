package bio.terra.rbs.service.cleanup;

import static bio.terra.rbs.app.configuration.CrlConfiguration.CLIENT_NAME;
import static bio.terra.rbs.app.configuration.CrlConfiguration.TEST_RESOURCE_TIME_TO_LIVE;
import static org.hamcrest.MatcherAssert.assertThat;
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

public class CleanupSchedulerTest extends BaseUnitTest {
  private static final Instant CREATION = Instant.now();

  /** Path to the service account credentials file. */
  private static final String GOOGLE_SERVICE_ACCOUNT_ADMIN_PATH =
      "rendered/janitor-client-sa-account.json";

  @Mock private final Publisher mockPublisher = mock(Publisher.class);

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
  public void setUp() {
    crlConfiguration.setCleanupAfterHandout(true);
    crlConfiguration.setJanitorClientCredentialFilePath(GOOGLE_SERVICE_ACCOUNT_ADMIN_PATH);
    crlConfiguration.setJanitorTrackResourceProjectId("projectId");
    crlConfiguration.setJanitorTrackResourceTopicId("topicId");
    cleanupScheduler =
        new CleanupScheduler(
            rbsDao, crlConfiguration, Clock.fixed(CREATION, ZoneId.systemDefault()));
    cleanupScheduler.providePublisher(mockPublisher);
  }

  @AfterEach
  public void tearDown() {
    // Shutdown the FlightScheduler so that it isn't running during other tests.
    if (cleanupScheduler != null) {
      cleanupScheduler.shutdown();
    }
  }

  private static Resource newResource(PoolId poolId, ResourceState state) {
    return Resource.builder()
        .id(ResourceId.create(UUID.randomUUID()))
        .poolId(poolId)
        .creation(Instant.now())
        .state(state)
        .build();
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

    CloudResourceUid cloudResourceUid1 =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p1"));
    CloudResourceUid cloudResourceUid2 =
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId("p2"));
    Resource handedOutR1 = newResource(pool.id(), ResourceState.READY);
    Resource handedOutR2 = newResource(pool.id(), ResourceState.READY);
    rbsDao.createPools(ImmutableList.of(pool));
    rbsDao.createResource(handedOutR1);
    rbsDao.createResource(handedOutR2);
    rbsDao.updateResourceAsReady(handedOutR1.id(), cloudResourceUid1);
    rbsDao.updateResourceAsReady(handedOutR2.id(), cloudResourceUid2);
    rbsDao.updateResourceAsHandedOut(handedOutR1.id(), RequestHandoutId.create("1111"));
    rbsDao.updateResourceAsHandedOut(handedOutR2.id(), RequestHandoutId.create("2222"));

    CreateResourceRequestBody MESSAGE_BODY =
        new CreateResourceRequestBody()
            .creation(CREATION.atOffset(ZoneOffset.UTC))
            .expiration(CREATION.plus(TEST_RESOURCE_TIME_TO_LIVE).atOffset(ZoneOffset.UTC))
            .putLabelsItem("client", CLIENT_NAME);

    cleanupScheduler.initialize();

    verify(mockPublisher, times(2)).publish(messageArgumentCaptor.capture());

    assertThat(
        messageArgumentCaptor.getAllValues().stream()
            .map(m -> m.getData().toStringUtf8())
            .collect(Collectors.toList()),
        Matchers.containsInAnyOrder(
            objectMapper.writeValueAsString(
                MESSAGE_BODY.resourceUid(
                    new bio.terra.janitor.model.CloudResourceUid()
                        .googleProjectUid(
                            new bio.terra.janitor.model.GoogleProjectUid().projectId("p1")))),
            objectMapper.writeValueAsString(
                MESSAGE_BODY.resourceUid(
                    new bio.terra.janitor.model.CloudResourceUid()
                        .googleProjectUid(
                            new bio.terra.janitor.model.GoogleProjectUid().projectId("p2"))))));
  }
}
