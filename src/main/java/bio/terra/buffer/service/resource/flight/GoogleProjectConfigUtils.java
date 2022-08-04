package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.BigQueryQuotas;
import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.buffer.generated.model.ServiceUsage;
import bio.terra.buffer.generated.model.Storage;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Utility methods for parsing the Google Project configuration. */
public class GoogleProjectConfigUtils {
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
    if (gcpProjectConfig.getSecurityGroup() != null) {
      String secGroup = gcpProjectConfig.getSecurityGroup().trim();
      if (!secGroup.isEmpty()) {
        return Optional.of(secGroup);
      }
    }
    return Optional.empty();
  }
}
