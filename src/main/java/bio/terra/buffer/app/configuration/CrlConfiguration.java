package bio.terra.buffer.app.configuration;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.common.cleanup.CleanupConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dns.DnsCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.cloudres.google.storage.StorageCow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManagerScopes;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.dns.Dns;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.IamScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuration to use Terra Cloud Resource Library and Janitor to manage CRL created resources.
 */
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "buffer.crl")
public class CrlConfiguration {
  /** The client name required by CRL. */
  public static final String CLIENT_NAME = "terra-resource-buffer";
  /** How long to keep the resource before Janitor do the cleanup. */
  public static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofMinutes(60);

  /**
   * Whether we're running Resource Buffer Service in test mode with Cloud Resource Library. If so,
   * we enable to the Janitor to auto-delete all created cloud resources.
   */
  private boolean testingMode = false;

  /**
   * Whether to publish message to Janitor to cleanup resource after it is handed out. It is true
   * only when the Resource Buffer Service is used to buffer resources for Resource Buffer Service
   * clients' test. For multiple instance Resource Buffer Service(not likely for testing Resource
   * Buffer Service), only turn it on for primary Resource Buffer Service.
   *
   * <p>We also have a {@code testingMode} flag, that will only be turned on for Resource Buffer
   * Service's test to delete resource created by Resource Buffer Service integration test.
   */
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

  public String getJanitorTrackResourceProjectId() {
    return janitorTrackResourceProjectId;
  }

  public String getJanitorTrackResourceTopicId() {
    return janitorTrackResourceTopicId;
  }

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

  public void setCleanupAfterHandout(boolean cleanupAfterHandout) {
    this.cleanupAfterHandout = cleanupAfterHandout;
  }

  /**
   * The {@link ClientConfig} in CRL's COW object. If in test, it will also include {@link
   * CleanupConfig}.
   */
  @Bean
  @Lazy
  public ClientConfig clientConfig() {
    ClientConfig.Builder builder = ClientConfig.Builder.newBuilder().setClient(CLIENT_NAME);
    if (testingMode) {
      builder.setCleanupConfig(
          CleanupConfig.builder()
              .setCleanupId(CLIENT_NAME + "-test")
              .setJanitorProjectId(janitorTrackResourceProjectId)
              .setTimeToLive(TEST_RESOURCE_TIME_TO_LIVE)
              .setJanitorTopicName(janitorTrackResourceTopicId)
              .setCredentials(loadJanitorClientCredential())
              .build());
    }
    return builder.build();
  }

  /** The CRL {@link CloudResourceManagerCow} which wrappers Google Cloud Resource Manager API. */
  @Bean
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

  /** The CRL {@link CloudBillingClientCow} which wrappers Google Billing API. */
  @Bean
  @Lazy
  public CloudBillingClientCow cloudBillingClientCow() throws IOException {
    return new CloudBillingClientCow(clientConfig(), GoogleCredentials.getApplicationDefault());
  }

  /** The CRL {@link ServiceUsageCow} which wrappers Google Cloud ServiceUsage API. */
  @Bean
  @Lazy
  public ServiceUsageCow serviceUsageCow() throws GeneralSecurityException, IOException {
    return ServiceUsageCow.create(clientConfig(), GoogleCredentials.getApplicationDefault());
  }

  /** The CRL {@link CloudComputeCow} which wrappers Google Compute API. */
  @Bean
  @Lazy
  public CloudComputeCow cloudComputeCow() throws IOException, GeneralSecurityException {
    return new CloudComputeCow(
        clientConfig(),
        new Compute.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                Defaults.jsonFactory(),
                setHttpTimeout(
                    new HttpCredentialsAdapter(
                        GoogleCredentials.getApplicationDefault()
                            .createScoped(ComputeScopes.all()))))
            .setApplicationName(CLIENT_NAME));
  }

  /** The CRL {@link DnsCow} which wrappers Google Compute API. */
  @Bean
  @Lazy
  public DnsCow dnsCow() throws IOException, GeneralSecurityException {
    return new DnsCow(
        clientConfig(),
        new Dns.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                Defaults.jsonFactory(),
                setHttpTimeout(
                    new HttpCredentialsAdapter(
                        GoogleCredentials.getApplicationDefault()
                            .createScoped(ComputeScopes.all()))))
            .setApplicationName(CLIENT_NAME));
  }

  /** The CRL {@link StorageCow} which wrappers Google Compute API. */
  @Bean
  @Lazy
  public StorageCow storageCow() throws IOException, GeneralSecurityException {
    return new StorageCow(clientConfig(), StorageOptions.getDefaultInstance());
  }

  /** The CRL {@link IamCow} which wrappers Google IAM API. */
  @Bean
  @Lazy
  public IamCow iamCow() throws IOException, GeneralSecurityException {
    return new IamCow(
        clientConfig(),
        new Iam.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                Defaults.jsonFactory(),
                setHttpTimeout(
                    new HttpCredentialsAdapter(
                        GoogleCredentials.getApplicationDefault().createScoped(IamScopes.all()))))
            .setApplicationName(CLIENT_NAME));
  }

  /** Loads the Janitor client service account credential from file. */
  public ServiceAccountCredentials loadJanitorClientCredential() {
    try {
      return ServiceAccountCredentials.fromStream(
          new FileInputStream(janitorClientCredentialFilePath));
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to load Janitor GoogleCredentials from configuration"
              + janitorClientCredentialFilePath,
          e);
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
