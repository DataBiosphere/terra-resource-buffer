package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.BigQueryQuotas;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ServiceUsage;
import bio.terra.buffer.generated.model.Storage;
import com.google.api.services.compute.model.Firewall;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/** Utility methods for parsing the Google Project configuration. */
public class GoogleProjectConfigUtils {

  /**
   * All current Google Compute Engine regions with the default Ip ranges listed (and manually
   * copied) in: https://cloud.google.com/vpc/docs/vpc#ip-ranges.
   */
  public static final Map<String, String> REGION_TO_IP_RANGE =
      ImmutableMap.<String, String>builder()
          .put("asia-east1", "10.140.0.0/20")
          .put("asia-east2", "10.170.0.0/20")
          .put("asia-northeast1", "10.146.0.0/20")
          .put("asia-northeast2", "10.174.0.0/20")
          .put("asia-northeast3", "10.178.0.0/20")
          .put("asia-south1", "10.160.0.0/20")
          .put("asia-southeast1", "10.148.0.0/20")
          .put("asia-southeast2", "10.184.0.0/20")
          .put("australia-southeast1", "10.152.0.0/20")
          .put("europe-central2", "10.186.0.0/20")
          .put("europe-north1", "10.166.0.0/20")
          .put("europe-west1", "10.132.0.0/20")
          .put("europe-west2", "10.154.0.0/20")
          .put("europe-west3", "10.156.0.0/20")
          .put("europe-west4", "10.164.0.0/20")
          .put("europe-west6", "10.172.0.0/20")
          .put("northamerica-northeast1", "10.162.0.0/20")
          .put("northamerica-northeast2", "10.188.0.0/20")
          .put("southamerica-east1", "10.158.0.0/20")
          .put("us-central1", "10.128.0.0/20")
          .put("us-east1", "10.142.0.0/20")
          .put("us-east4", "10.150.0.0/20")
          .put("us-west1", "10.138.0.0/20")
          .put("us-west2", "10.168.0.0/20")
          .put("us-west3", "10.180.0.0/20")
          .put("us-west4", "10.182.0.0/20")
          .build();

  /** Checks if network monitoring is enabled from config. */
  public static boolean isNetworkMonitoringEnabled(GcpProjectConfig gcpProjectConfig) {
    return gcpProjectConfig.getNetwork() != null
        && gcpProjectConfig.getNetwork().isEnableNetworkMonitoring() != null
        && gcpProjectConfig.getNetwork().isEnableNetworkMonitoring();
  }

  /** Checks the config to see if we should keep the default network. */
  public static boolean keepDefaultNetwork(GcpProjectConfig gcpProjectConfig) {
    // If network object or keepDefaultNetwork flag are not defined, then use default value =
    // false.
    return gcpProjectConfig.getNetwork() != null
        && gcpProjectConfig.getNetwork().isKeepDefaultNetwork() != null
        && gcpProjectConfig.getNetwork().isKeepDefaultNetwork();
  }

  /** Checks the config to see if we should keep the default compute engine service account. */
  public static boolean keepComputeEngineServiceAcct(GcpProjectConfig gcpProjectConfig) {
    // If computeEngine object or keepDefaultServiceAcct flag are not defined, then use default
    // value = false.
    return gcpProjectConfig.getComputeEngine() != null
        && gcpProjectConfig.getComputeEngine().isKeepDefaultServiceAcct() != null
        && gcpProjectConfig.getComputeEngine().isKeepDefaultServiceAcct();
  }

  /**
   * Checks if private Google Access enabled. Using network monitoring requires private Google
   * Access. So if {@link #isNetworkMonitoringEnabled(GcpProjectConfig)} is true, this will also be
   * true regardless.
   */
  public static boolean usePrivateGoogleAccess(GcpProjectConfig gcpProjectConfig) {
    return isNetworkMonitoringEnabled(gcpProjectConfig)
        || (gcpProjectConfig.getNetwork() != null
            && gcpProjectConfig.getNetwork().isEnablePrivateGoogleAccess() != null
            && gcpProjectConfig.getNetwork().isEnablePrivateGoogleAccess());
  }

  /** Checks if private Google Access enabled for gcr.io. */
  public static boolean enableGcrPrivateGoogleAccess(GcpProjectConfig gcpProjectConfig) {
    return gcpProjectConfig.getNetwork() != null
        && gcpProjectConfig.getNetwork().isEnableCloudRegistryPrivateGoogleAccess() != null
        && gcpProjectConfig.getNetwork().isEnableCloudRegistryPrivateGoogleAccess();
  }

  /** Whether to allow CGP VMs have internet access. */
  public static boolean blockBatchInternetAccess(GcpProjectConfig gcpProjectConfig) {
    return gcpProjectConfig.getNetwork() != null
        && gcpProjectConfig.getNetwork().isBlockBatchInternetAccess() != null
        && gcpProjectConfig.getNetwork().isBlockBatchInternetAccess();
  }

  /** Whether to create NAT gateway per regions. */
  public static boolean enableNatGateway(GcpProjectConfig gcpProjectConfig) {
    return gcpProjectConfig.getNetwork() != null
        && gcpProjectConfig.getNetwork().isEnableNatGateway() != null
        && gcpProjectConfig.getNetwork().isEnableNatGateway();
  }

  /** Gets blocked regions. */
  public static List<String> blockedRegions(GcpProjectConfig gcpProjectConfig) {
    if (gcpProjectConfig.getNetwork() == null
        || gcpProjectConfig.getNetwork().getBlockedRegions() == null) {
      return Collections.emptyList();
    }
    return gcpProjectConfig.getNetwork().getBlockedRegions();
  }

  /** Create the GCS bucket for log storage if enabled in configuration. */
  public static boolean createLogBucket(GcpProjectConfig gcpProjectConfig) {
    return Optional.ofNullable(gcpProjectConfig.getStorage())
        .map(Storage::isCreateLogBucket) // returns Optional.empty() if null
        .orElse(true);
  }

  /** Create a service account for running GKE node. */
  public static boolean createGkeDefaultSa(GcpProjectConfig gcpProjectConfig) {
    return gcpProjectConfig.getKubernetesEngine() != null
        && gcpProjectConfig.getKubernetesEngine().isCreateGkeDefaultServiceAccount() != null
        && gcpProjectConfig.getKubernetesEngine().isCreateGkeDefaultServiceAccount();
  }

  /**
   * Create a Consumer Quota Override for BigQuery Daily Query Usage. If the configuration for
   * isOverrideBigQueryDailyUsageQuota is true and bigQueryDailyUsageQuotaOverrideValueBytes is
   * non-null, return an Optional of the value in bigQueryDailyUsageQuotaOverrideValueBytes.
   * Otherwise, return empty.
   */
  public static Optional<Long> bigQueryDailyUsageOverrideValueMebibytes(
      GcpProjectConfig gcpProjectConfig) {
    Optional<BigQueryQuotas> bigQueryQuotasMaybe =
        Optional.ofNullable(gcpProjectConfig.getServiceUsage()).map(ServiceUsage::getBigQuery);
    if (bigQueryQuotasMaybe.isEmpty()) {
      return Optional.empty();
    }
    BigQueryQuotas bigQueryQuotas = bigQueryQuotasMaybe.get();
    if (!bigQueryQuotas.isOverrideBigQueryDailyUsageQuota()
        || null == bigQueryQuotas.getBigQueryDailyUsageQuotaOverrideValueMebibytes()) {
      return Optional.empty();
    }
    long value = bigQueryQuotas.getBigQueryDailyUsageQuotaOverrideValueMebibytes().longValue();
    return Optional.of(value);
  }

  public static Optional<String> getSecurityGroup(GcpProjectConfig gcpProjectConfig) {
    return Optional.ofNullable(
        Strings.emptyToNull(
            (gcpProjectConfig.getSecurityGroup() != null)
                ? gcpProjectConfig.getSecurityGroup().trim()
                : null));
  }

  /** Gets a map of region to IP range. */
  public static Map<String, String> getRegionToIpRange(GcpProjectConfig gcpProjectConfig) {
    List<String> blockedRegions = GoogleProjectConfigUtils.blockedRegions(gcpProjectConfig);
    return REGION_TO_IP_RANGE.entrySet().stream()
        .filter(e -> !blockedRegions.contains(e.getKey()))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }
  /** append target tags for VM instances that should be applied the internal ingress rules. */
  public static Firewall appendInternalIngressTargetTags(
      Firewall firewall, GcpProjectConfig gcpProjectConfig) {
    if (gcpProjectConfig.getNetwork() == null) {
      return firewall;
    }
    List<String> tags = gcpProjectConfig.getNetwork().getInternalAccessTargetTags();
    if (tags != null && !tags.isEmpty()) {
      firewall.setTargetTags(tags);
    }
    return firewall;
  }
}
