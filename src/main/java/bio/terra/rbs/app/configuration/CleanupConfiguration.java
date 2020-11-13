package bio.terra.rbs.app.configuration;

import com.google.auth.oauth2.ServiceAccountCredentials;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Configuration to use Terra Cloud Resource Libraty */
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "rbs.cleanup")
public class CleanupConfiguration {
  /** How long to keep the resource before Janitor do the cleanup. */
  public static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofMinutes(60);

  /** Whether to publish message to Janitor to cleanup resource after it is handed out. */
  private boolean cleanupAfterHandout = false;

  /** Credential file path to be able to publish message to Janitor */
  private String janitorClientCredentialFilePath;

  /** pubsub project id to publish track resource to Janitor */
  private String janitorTrackResourceProjectId;

  /** pubsub topic id to publish track resource to Janitor */
  private String janitorTrackResourceTopicId;

  public boolean isCleanupAfterHandout() {
    return cleanupAfterHandout;
  }

  public String getJanitorClientCredentialFilePath() {
    return janitorClientCredentialFilePath;
  }

  public String getJanitorTrackResourceProjectId() {
    return janitorTrackResourceProjectId;
  }

  public String getJanitorTrackResourceTopicId() {
    return janitorTrackResourceTopicId;
  }

  public void setJanitorClientCredentialFilePath(String janitorClientCredentialFilePath) {
    this.janitorClientCredentialFilePath = janitorClientCredentialFilePath;
  }

  public void setJanitorTrackResourceProjectId(String janitorTrackResourceProjectId) {
    this.janitorTrackResourceProjectId = janitorTrackResourceProjectId;
  }

  public void setJanitorTrackResourceTopicId(String janitorTrackResourceTopicId) {
    this.janitorTrackResourceTopicId = janitorTrackResourceTopicId;
  }

  public void setCleanupAfterHandout(boolean cleanupAfterHandout) {
    this.cleanupAfterHandout = cleanupAfterHandout;
  }

  /** Gets the Janitor client service account credential. */
  public ServiceAccountCredentials getJanitorClientCredential() {
    try {
      return ServiceAccountCredentials.fromStream(
          Thread.currentThread()
              .getContextClassLoader()
              .getResourceAsStream(janitorClientCredentialFilePath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load Janitor GoogleCredentials from configuration"
              + janitorClientCredentialFilePath,
          e);
    }
  }
}
