package bio.terra.rbs.service.cleanup;

import static bio.terra.rbs.app.configuration.CrlConfiguration.CLIENT_NAME;
import static bio.terra.rbs.app.configuration.CrlConfiguration.TEST_RESOURCE_TIME_TO_LIVE;

import bio.terra.janitor.model.CreateResourceRequestBody;
import bio.terra.rbs.app.configuration.CrlConfiguration;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.CloudResourceUid;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Scheduler service to publish message to Janitor to cleanup resource. */
@Component
public class CleanupScheduler {
  // Number of message to publish per scheduler run.
  private static final Integer MESSAGE_TO_PUBLISH_PER_RUN = 100;

  private final Logger logger = LoggerFactory.getLogger(CleanupScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final RbsDao rbsDao;
  private final CrlConfiguration crlConfiguration;

  private Clock clock;
  private ObjectMapper objectMapper;
  private Publisher publisher;

  @Autowired
  public CleanupScheduler(RbsDao rbsDao, CrlConfiguration crlConfiguration) {
    this.rbsDao = rbsDao;
    this.crlConfiguration = crlConfiguration;
  }

  /** Provides an {@link Publisher}. */
  @VisibleForTesting
  void providePublisher(Publisher newPublisher) {
    this.publisher = newPublisher;
  }

  /** Initialize the CleanupScheduler, kicking off its tasks. */
  public void initialize() {
    if (crlConfiguration.isCleanupAfterHandout()) {
      logger.info("Rbs cleanup scheduling enabled.");
    } else {
      // Do nothing if scheduling is disabled.
      logger.info("Rbs cleanup scheduling disabled.");
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
                    FixedCredentialsProvider.create(crlConfiguration.getJanitorClientCredential()))
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
    this.clock = Clock.systemUTC();

    // The scheduled task will not execute concurrently with itself even if it takes a long time.
    // See javadoc on ScheduledExecutorService#scheduleAtFixedRate.
    executor.scheduleAtFixedRate(
        new LogThrowables(this::scheduleCleanup),
        /* initialDelay= */ 0,
        /* period= */ 10,
        TimeUnit.MINUTES);
  }

  public void scheduleCleanup() {
    List<Resource> resources = rbsDao.retrieveResourceToCleanup(MESSAGE_TO_PUBLISH_PER_RUN);
    for (Resource resource : resources) {
      publish(resource.cloudResourceUid());
      rbsDao.insertCleanupRecord(resource.id());
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
              .expiration(Instant.now().plus(TEST_RESOURCE_TIME_TO_LIVE).atOffset(ZoneOffset.UTC))
              .expiration(now.plus(TEST_RESOURCE_TIME_TO_LIVE))
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

  public void shutdown() {
    // Don't schedule  anything new during shutdown.
    executor.shutdown();
  }

  /**
   * Wraps a runnable to log any thrown errors to allow the runnable to still be run with a {@link
   * ScheduledExecutorService}.
   *
   * <p>ScheduledExecutorService scheduled tasks that throw errors stop executing.
   */
  private class LogThrowables implements Runnable {
    private final Runnable task;

    private LogThrowables(Runnable task) {
      this.task = task;
    }

    @Override
    public void run() {
      try {
        task.run();
      } catch (Throwable t) {
        logger.error(
            "Caught exception in CleanupSchedule ScheduledExecutorService. StackTrace:\n"
                + t.getStackTrace(),
            t);
      }
    }
  }
}
