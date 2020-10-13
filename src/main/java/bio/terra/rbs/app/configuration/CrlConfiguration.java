package bio.terra.rbs.app.configuration;

import static bio.terra.rbs.app.configuration.BeanNames.CRL_CLIENT_CONFIG;
import static bio.terra.rbs.app.configuration.BeanNames.GOOGLE_RM_COW;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.CloudResourceManagerScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Configuration to use Terra Cloud Resource Libraty */
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "rbs.crl")
public class CrlConfiguration {
  /** The client name required by CRL. */
  public static final String CLIENT_NAME = "terra-rbs";
  /** How long to keep the resource before the 'prod' Janitor do the cleanup. */
  public static final Duration RESOURCE_TIME_TO_LIVE_PROD = Duration.ofMinutes(30);

  public boolean isTestingMode() {
    return testingMode;
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

  /**
   * Whether to use crl in test. If in test, we need Janitor to cleanup the resource after creation.
   */
  private boolean testingMode;

  /** Credential file path to be able to publish message to Janitor test env (toolsalpha). */
  private String janitorClientCredentialFilePath;

  /** pubsub project id to publish track resource to Janitor prod env(tools) */
  private String janitorTrackResourceProjectId;

  /** pubsub topic id to publish track resource to Janitor prod env(tools) */
  private String janitorTrackResourceTopicId;

  public void setTestingMode(boolean testingMode) {
    this.testingMode = testingMode;
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

  /**
   * The {@link ClientConfig} in CRL's COW object. If in test, it will also include {@link
   * CleanupConfig}.
   */
  @Bean(CRL_CLIENT_CONFIG)
  @Lazy
  public ClientConfig clientConfig() {
    ClientConfig.Builder builder = ClientConfig.Builder.newBuilder().setClient(CLIENT_NAME);
    if (testingMode) {
      builder.setCleanupConfig(
          CleanupConfig.builder()
              .setCleanupId(CLIENT_NAME + "-test")
              .setJanitorProjectId(janitorTrackResourceProjectId)
              .setTimeToLive(RESOURCE_TIME_TO_LIVE_PROD)
              .setJanitorTopicName(janitorTrackResourceTopicId)
              .setCredentials(getGoogleCredentialsOrDie(janitorClientCredentialFilePath))
              .build());
    }
    return builder.build();
  }

  /** The CRL {@link CloudResourceManagerCow} which wrappers Google Cloud Resource Manager API. */
  @Bean(GOOGLE_RM_COW)
  @Lazy
  public CloudResourceManagerCow cloudResourceManagerCow()
      throws IOException, GeneralSecurityException {
    return new CloudResourceManagerCow(
        clientConfig(),
        new CloudResourceManager.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                Defaults.jsonFactory(),
                setHttpTimeout(
                    new HttpCredentialsAdapter(
                        GoogleCredentials.getApplicationDefault()
                            .createScoped(CloudResourceManagerScopes.all()))))
            .setApplicationName(CLIENT_NAME));
  }

  private static ServiceAccountCredentials getGoogleCredentialsOrDie(String serviceAccountPath) {
    try {
      return ServiceAccountCredentials.fromStream(
          Thread.currentThread().getContextClassLoader().getResourceAsStream(serviceAccountPath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load GoogleCredentials from configuration" + serviceAccountPath, e);
    }
  }

  /** Sets longer timeout because ResourceManager operation may take longer than default timeout. */
  private static HttpRequestInitializer setHttpTimeout(
      final HttpRequestInitializer requestInitializer) {
    return httpRequest -> {
      requestInitializer.initialize(httpRequest);
      httpRequest.setConnectTimeout(5 * 60000); // 5 minutes connect timeout
      httpRequest.setReadTimeout(5 * 60000); // 5 minutes read timeout
    };
  }
}
