package bio.terra.rbs.app.configuration;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.cloudres.google.storage.StorageCow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.CloudResourceManagerScopes;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.dns.Dns;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;

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
              Thread.currentThread().getContextClassLoader().getResourceAsStream(janitorClientCredentialFilePath));
    } catch (Exception e) {
      throw new RuntimeException(
              "Unable to load Janitor GoogleCredentials from configuration" + janitorClientCredentialFilePath, e);
    }
  }
}
