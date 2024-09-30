package bio.terra.buffer.service.cleanup;

import static bio.terra.buffer.app.configuration.CrlConfiguration.CLIENT_NAME;

import bio.terra.buffer.app.configuration.CrlConfiguration;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.db.BufferDao;
import bio.terra.buffer.generated.model.CloudResourceUid;
import bio.terra.janitor.model.CreateResourceRequestBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduler service to publish message to Janitor to cleanup resource. */
@Component
public class JanitorResourceCleanupScheduler {
  // Number of message to publish per scheduler run.
  private static final Integer MESSAGE_TO_PUBLISH_PER_RUN = 100;

  private final Logger logger = LoggerFactory.getLogger(JanitorResourceCleanupScheduler.class);

  private final BufferDao bufferDao;
  private final CrlConfiguration crlConfiguration;
  private final Clock clock;

  private ObjectMapper objectMapper;
  private Publisher publisher;

  @Autowired
  public JanitorResourceCleanupScheduler(
      BufferDao bufferDao, CrlConfiguration crlConfiguration, Clock clock) {
    this.bufferDao = bufferDao;
    this.crlConfiguration = crlConfiguration;
    this.clock = clock;
  }

  /** Provides an {@link Publisher}. */
  @VisibleForTesting
  void providePublisher(Publisher newPublisher) {
    this.publisher = newPublisher;
  }

  /** Initialize the CleanupScheduler, kicking off its tasks. */
  public void initialize() {
    if (crlConfiguration.janitorConfigured()) {
      logger.info("Buffer cleanup scheduling enabled.");
    } else {
      // Do nothing if Janitor is not configured.
      logger.info("Buffer cleanup scheduling disabled because Janitor is not configured.");
      return;
    }
    if (publisher == null) {
      TopicName topicName =
          TopicName.of(
              crlConfiguration.getJanitorTrackResourceProjectId(),
              crlConfiguration.getJanitorTrackResourceTopicId());
      try {
        this.publisher =
            Publisher.newBuilder(topicName)
                .setCredentialsProvider(
                    FixedCredentialsProvider.create(crlConfiguration.loadJanitorClientCredential()))
                .build();
      } catch (IOException e) {
        throw new RuntimeException("Failed to create publisher", e);
      }
    }
    this.objectMapper =
        new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
  }

  @Scheduled(cron = "${buffer.crl.publish-schedule}")
  @SchedulerLock(name = "CRLJanitor", lockAtMostFor = "PT10M")
  public void scheduleCleanup() {
    List<Resource> resources =
        bufferDao.retrieveResourceToCleanup(
            MESSAGE_TO_PUBLISH_PER_RUN, crlConfiguration.isCleanupAfterHandout());
    for (Resource resource : resources) {
      publish(resource.cloudResourceUid());
      bufferDao.insertCleanupRecord(resource.id());
    }
  }

  private void publish(CloudResourceUid cloudResourceUid) {
    ByteString data;
    try {
      OffsetDateTime now = OffsetDateTime.now(clock);
      CreateResourceRequestBody body =
          new CreateResourceRequestBody()
              .resourceUid(
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(cloudResourceUid),
                      bio.terra.janitor.model.CloudResourceUid.class))
              .creation(now)
              .expiration(now.plus(crlConfiguration.getTestResourceTimeToLive()))
              .putLabelsItem("client", CLIENT_NAME);
      data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(body));
    } catch (IOException e) {
      throw new RuntimeException(
          String.format(
              "Failed to build CreateResourceRequestBody for CloudResourceUid: [%s]",
              cloudResourceUid),
          e);
    }
    ApiFuture<String> messageIdFuture =
        publisher.publish(PubsubMessage.newBuilder().setData(data).build());
    try {
      String messageId = messageIdFuture.get();
      logger.debug("Publish message to Janitor track resource " + messageId);
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(
          String.format("Failed to publish message: [%s] ", data.toString()), e);
    }
  }
}
