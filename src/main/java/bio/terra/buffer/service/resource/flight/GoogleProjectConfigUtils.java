package bio.terra.buffer.service.resource.flight;

import bio.terra.buffer.generated.model.GcpProjectConfig;

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
  public static boolean isUsePrivateGoogleAccess(GcpProjectConfig gcpProjectConfig) {
    return isNetworkMonitoringEnabled(gcpProjectConfig)
        || (gcpProjectConfig.getNetwork() != null
            && gcpProjectConfig.getNetwork().isUsePrivateGoogleAccess() != null
            && gcpProjectConfig.getNetwork().isUsePrivateGoogleAccess());
  }
}
