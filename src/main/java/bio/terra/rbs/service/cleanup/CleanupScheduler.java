package bio.terra.rbs.service.cleanup;

import bio.terra.janitor.model.CreateResourceRequestBody;
import bio.terra.rbs.app.configuration.CrlConfiguration;
import bio.terra.rbs.common.Resource;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.CloudResourceUid;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static bio.terra.rbs.app.configuration.CrlConfiguration.CLIENT_NAME;
import static bio.terra.rbs.app.configuration.CrlConfiguration.TEST_RESOURCE_TIME_TO_LIVE;

/** Scheduler service to publish message to Janitor to cleanup resource. */
public class CleanupScheduler {
    private final Logger logger = LoggerFactory.getLogger(CleanupScheduler.class);

    /** Only need as many threads as we have scheduled tasks. */
    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
    private final RbsDao rbsDao;
    private final CrlConfiguration crlConfiguration;
    private ObjectMapper objectMapper;
    private Publisher publisher;

    @Autowired
    public CleanupScheduler(RbsDao rbsDao, CrlConfiguration crlConfiguration) {
        this.rbsDao = rbsDao;
        this.crlConfiguration = crlConfiguration;
    }

    /**
     * Initialize the CleanupScheduler, kicking off its tasks.
     */
    public void initialize() {
        if (crlConfiguration.isCleanupAfterHandout()) {
            logger.info("Rbs cleanup scheduling enabled.");
        } else {
            // Do nothing if scheduling is disabled.
            logger.info("Rbs cleanup scheduling disabled.");
            return;
        }
        TopicName topicName =
                TopicName.of(crlConfiguration.getJanitorTrackResourceProjectId(), crlConfiguration.getJanitorTrackResourceTopicId());

        try {
            this.publisher = Publisher.newBuilder(topicName)
                    .setCredentialsProvider(
                            FixedCredentialsProvider.create(crlConfiguration.getJanitorClientCredential()))
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new Jdk8Module())
                        .registerModule(new JavaTimeModule())
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // The scheduled task will not execute concurrently with itself even if it takes a long time.
        // See javadoc on ScheduledExecutorService#scheduleAtFixedRate.
        executor.scheduleAtFixedRate(
                this::scheduleCleanup,
                /* initialDelay= */ 0,
                /* period= */ 10,
                TimeUnit.MINUTES);
    }

    public void scheduleCleanup() {
        List<Resource> resources = rbsDao.retrieveResourceToCleanup(null, 10);
        for(Resource resource : resources) {
            publish(resource.cloudResourceUid());
        }

    }

    private void publish(CloudResourceUid resource) {
        ByteString data;
        try {
            CreateResourceRequestBody body =
                    new CreateResourceRequestBody()
                            .resourceUid(
                                    objectMapper.readValue(objectMapper.writeValueAsString(resource),
                                            bio.terra.janitor.model.CloudResourceUid.class))
                            .creation(Instant.now().atOffset(ZoneOffset.UTC))
                            .expiration(Instant.now().plus(TEST_RESOURCE_TIME_TO_LIVE).atOffset(ZoneOffset.UTC))
                            .putLabelsItem("client", CLIENT_NAME);
            data = ByteString.copyFromUtf8(objectMapper.writeValueAsString(body));
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Failed to serialize CreateResourceRequestBody: [%s]", body), e);
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
