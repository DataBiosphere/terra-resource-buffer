package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.REGION_TO_IP_RANGE;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.appendInternalIngressTargetTags;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.blockBatchInternetAccess;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.keepDefaultNetwork;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Network;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates firewall rules for the GCP project. */
public class CreateFirewallRuleStep implements Step {
  /** Names for firewall rules on the high-security network (called 'network'). */
  @VisibleForTesting
  public static final String ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME = "leonardo-allow-internal";

  @VisibleForTesting
  public static final String LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME = "leonardo-ssl";

  /**
   * Names for firewall rules on the default network (called 'default'). Rule names must be unique
   * within a project, so prefix these rule names with 'default-vpc'.
   */
  @VisibleForTesting
  public static final String ALLOW_INTERNAL_FOR_DEFAULT_NETWORK_RULE_NAME =
      DEFAULT_NETWORK_NAME + "-vpc-" + ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME;

  @VisibleForTesting
  public static final String LEONARDO_SSL_FOR_DEFAULT_NETWORK_RULE_NAME =
      DEFAULT_NETWORK_NAME + "-vpc-" + LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME;

  /**
   * Firewall rules to make VM private but still allowing leonardo can access internet(exclude
   * leonardo-private which is used by Leo created dataproc worker).
   */
  @VisibleForTesting public static final String DENY_EGRESS_RULE_NAME = "deny-egress-all";

  @VisibleForTesting
  public static final String DENY_EGRESS_LEONARDO_WORKER_RULE_NAME = "deny-egress-leonardo-private";

  @VisibleForTesting
  public static final String ALLOW_EGRESS_LEONARDO_RULE_NAME = "allow-egress-leonardo";

  @VisibleForTesting
  public static final String ALLOW_EGRESS_PRIVATE_ACCESS_RULE_NAME = "allow-egress-private-access";

  @VisibleForTesting
  public static final String ALLOW_EGRESS_INTERNAL_RULE_NAME = "allow-egress-internal";

  /**
   * Firewall rule to priority map. The lower number is, the higher priority.
   *
   * <p>To make sure only Leo VMs have private internet access when {@code blockBatchInternetAccess}
   * is true, the priority of egress firewall rules need to be:
   *
   * <ol>
   *   <li>ALLOW_EGRESS_PRIVATE_ACCESS_RULE_NAME
   *   <li>ALLOW_EGRESS_INTERNAL(equal)
   *   <li>DENY_EGRESS_LEONARDO_WORKER_RULE_NAME
   *   <li>ALLOW_EGRESS_LEONARDO
   *   <li>DENY_EGRESS_RULE_NAME
   * </ol>
   */
  @VisibleForTesting
  public static final ImmutableMap<String, Integer> FIREWALL_RULE_PRIORITY_MAP =
      ImmutableMap.<String, Integer>builder()
          .put(ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME, 1000)
          .put(LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME, 1000)
          .put(ALLOW_INTERNAL_FOR_DEFAULT_NETWORK_RULE_NAME, 1000)
          .put(LEONARDO_SSL_FOR_DEFAULT_NETWORK_RULE_NAME, 1000)
          .put(ALLOW_EGRESS_PRIVATE_ACCESS_RULE_NAME, 1000)
          .put(ALLOW_EGRESS_INTERNAL_RULE_NAME, 1000)
          .put(DENY_EGRESS_LEONARDO_WORKER_RULE_NAME, 2000)
          .put(ALLOW_EGRESS_LEONARDO_RULE_NAME, 3000)
          .put(DENY_EGRESS_RULE_NAME, 4000)
          .build();

  @VisibleForTesting
  public static final Firewall ALLOW_INTERNAL_VPC_NETWORK =
      new Firewall()
          .setName(ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME)
          .setDescription("Allow internal ingress traffic on the network.")
          .setDirection("INGRESS")
          .setTargetTags(Arrays.asList("leonardo"))
          .setSourceRanges(new ArrayList(REGION_TO_IP_RANGE.values()))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(ALLOW_INTERNAL_FOR_VPC_NETWORK_RULE_NAME))
          .setAllowed(
              Arrays.asList(
                  new Firewall.Allowed().setIPProtocol("icmp"),
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(Arrays.asList("0-65535")),
                  new Firewall.Allowed().setIPProtocol("udp").setPorts(Arrays.asList("0-65535"))));

  @VisibleForTesting
  public static final Firewall ALLOW_INTERNAL_DEFAULT_NETWORK =
      new Firewall()
          .setName(ALLOW_INTERNAL_FOR_DEFAULT_NETWORK_RULE_NAME)
          .setDescription("Allow internal ingress traffic on the default VPC network.")
          .setDirection("INGRESS")
          .setTargetTags(Arrays.asList("leonardo"))
          .setSourceRanges(new ArrayList(REGION_TO_IP_RANGE.values()))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(ALLOW_INTERNAL_FOR_DEFAULT_NETWORK_RULE_NAME))
          .setAllowed(
              Arrays.asList(
                  new Firewall.Allowed().setIPProtocol("icmp"),
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(Arrays.asList("0-65535")),
                  new Firewall.Allowed().setIPProtocol("udp").setPorts(Arrays.asList("0-65535"))));

  @VisibleForTesting
  public static final Firewall ALLOW_INGRESS_LEONARDO_SSL_NETWORK =
      new Firewall()
          .setName(LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME)
          .setDescription("Allow SSL traffic from Leonardo-managed VMs.")
          .setDirection("INGRESS")
          .setSourceRanges(Arrays.asList("0.0.0.0/0"))
          .setTargetTags(Arrays.asList("leonardo"))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(LEONARDO_SSL_FOR_VPC_NETWORK_RULE_NAME))
          .setAllowed(
              Arrays.asList(
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(Arrays.asList("443"))));

  @VisibleForTesting
  public static final Firewall ALLOW_INGRESS_LEONARDO_SSL_DEFAULT =
      new Firewall()
          .setName(LEONARDO_SSL_FOR_DEFAULT_NETWORK_RULE_NAME)
          .setDescription("Allow SSL traffic from Leonardo-managed VMs for default VPC network.")
          .setDirection("INGRESS")
          .setSourceRanges(Arrays.asList("0.0.0.0/0"))
          .setTargetTags(Arrays.asList("leonardo"))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(LEONARDO_SSL_FOR_DEFAULT_NETWORK_RULE_NAME))
          .setAllowed(
              Arrays.asList(
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(Arrays.asList("443"))));

  @VisibleForTesting
  public static final Firewall ALLOW_EGRESS_PRIVATE_ACCESS =
      new Firewall()
          .setName(ALLOW_EGRESS_PRIVATE_ACCESS_RULE_NAME)
          .setDescription("Allow accessing internet using private Google Access.")
          .setDirection("EGRESS")
          .setDestinationRanges(Arrays.asList(RESTRICTED_GOOGLE_IP_ADDRESS))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(ALLOW_EGRESS_PRIVATE_ACCESS_RULE_NAME))
          .setAllowed(Arrays.asList(new Firewall.Allowed().setIPProtocol("all")));

  @VisibleForTesting
  public static final Firewall ALLOW_EGRESS_INTERNAL =
      new Firewall()
          .setName(ALLOW_EGRESS_INTERNAL_RULE_NAME)
          .setDescription("Allow internal egress traffic on the network.")
          .setDirection("EGRESS")
          .setDestinationRanges(new ArrayList(REGION_TO_IP_RANGE.values()))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(ALLOW_EGRESS_INTERNAL_RULE_NAME))
          .setAllowed(
              Arrays.asList(
                  new Firewall.Allowed().setIPProtocol("icmp"),
                  new Firewall.Allowed().setIPProtocol("tcp").setPorts(Arrays.asList("0-65535")),
                  new Firewall.Allowed().setIPProtocol("udp").setPorts(Arrays.asList("0-65535"))));

  @VisibleForTesting
  public static final Firewall ALLOW_EGRESS_LEONARDO =
      new Firewall()
          .setName(ALLOW_EGRESS_LEONARDO_RULE_NAME)
          .setDescription("Allow Leonardo created VMs accessing internet")
          .setDirection("EGRESS")
          .setDestinationRanges(Arrays.asList("0.0.0.0/0"))
          .setTargetTags(Arrays.asList("leonardo"))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(ALLOW_EGRESS_LEONARDO_RULE_NAME))
          .setAllowed(Arrays.asList(new Firewall.Allowed().setIPProtocol("all")));

  @VisibleForTesting
  public static final Firewall DENY_EGRESS_LEONARDO_WORKER =
      new Firewall()
          .setName(DENY_EGRESS_LEONARDO_WORKER_RULE_NAME)
          .setDescription("Block leonardo-private VMs accessing internet")
          .setDirection("EGRESS")
          .setDestinationRanges(Arrays.asList("0.0.0.0/0"))
          .setTargetTags(Arrays.asList("leonardo-private"))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(DENY_EGRESS_LEONARDO_WORKER_RULE_NAME))
          .setDenied(Arrays.asList(new Firewall.Denied().setIPProtocol("all")));

  public static final Firewall DENY_EGRESS =
      new Firewall()
          .setName(DENY_EGRESS_RULE_NAME)
          .setDescription("Block egress for all VMs")
          .setDirection("EGRESS")
          .setDestinationRanges(Arrays.asList("0.0.0.0/0"))
          .setPriority(FIREWALL_RULE_PRIORITY_MAP.get(DENY_EGRESS_RULE_NAME))
          .setDenied(Arrays.asList(new Firewall.Denied().setIPProtocol("all")));

  private final Logger logger = LoggerFactory.getLogger(CreateFirewallRuleStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateFirewallRuleStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      // Keep track of operations to poll for completion.
      List<OperationCow<?>> operationsToPoll = new ArrayList<>();

      // Network is already created and checked in previous step so here won't be empty.
      // If we got NPE, that means something went wrong with GCP, fine to just throw NPE here.
      Network highSecurityNetwork =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();

      addFirewallRule(
              projectId,
              appendNetworkOnFirewall(
                  highSecurityNetwork,
                  appendInternalIngressTargetTags(ALLOW_INTERNAL_VPC_NETWORK, gcpProjectConfig)))
          .ifPresent(operationsToPoll::add);
      addFirewallRule(
              projectId,
              appendNetworkOnFirewall(highSecurityNetwork, ALLOW_INGRESS_LEONARDO_SSL_NETWORK))
          .ifPresent(operationsToPoll::add);

      // TODO(PF-538): revisit whether we still need this flag after NF allows specifying a network
      // If the default network was not deleted, then create identical firewall rules for it.
      if (keepDefaultNetwork(gcpProjectConfig)) {
        Network defaultNetwork =
            getResource(
                    () -> computeCow.networks().get(projectId, DEFAULT_NETWORK_NAME).execute(), 404)
                .get();

        addFirewallRule(
                projectId, appendNetworkOnFirewall(defaultNetwork, ALLOW_INTERNAL_DEFAULT_NETWORK))
            .ifPresent(operationsToPoll::add);
        addFirewallRule(
                projectId,
                appendNetworkOnFirewall(defaultNetwork, ALLOW_INGRESS_LEONARDO_SSL_DEFAULT))
            .ifPresent(operationsToPoll::add);
      }

      if (blockBatchInternetAccess(gcpProjectConfig)) {
        addFirewallRule(
                projectId, appendNetworkOnFirewall(highSecurityNetwork, ALLOW_EGRESS_INTERNAL))
            .ifPresent(operationsToPoll::add);
        addFirewallRule(
                projectId,
                appendNetworkOnFirewall(highSecurityNetwork, ALLOW_EGRESS_PRIVATE_ACCESS))
            .ifPresent(operationsToPoll::add);
        addFirewallRule(
                projectId, appendNetworkOnFirewall(highSecurityNetwork, ALLOW_EGRESS_LEONARDO))
            .ifPresent(operationsToPoll::add);
        addFirewallRule(
                projectId,
                appendNetworkOnFirewall(highSecurityNetwork, DENY_EGRESS_LEONARDO_WORKER))
            .ifPresent(operationsToPoll::add);
        addFirewallRule(projectId, appendNetworkOnFirewall(highSecurityNetwork, DENY_EGRESS))
            .ifPresent(operationsToPoll::add);
      }

      for (OperationCow<?> operation : operationsToPoll) {
        pollUntilSuccess(operation, Duration.ofSeconds(3), Duration.ofMinutes(5));
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating firewall rule", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP.
    return StepResult.getStepResultSuccess();
  }

  /**
   * Helper method to add a firewall rule to the project. Ignores conflicts if the rule already
   * exists.
   *
   * @param projectId project where the network lives
   * @param rule firewall rule object to add to the project
   * @return pointer to the operation to poll for completion
   */
  private Optional<OperationCow<?>> addFirewallRule(String projectId, Firewall rule)
      throws IOException {
    return createResourceAndIgnoreConflict(
            () -> computeCow.firewalls().insert(projectId, rule).execute())
        .map(
            insertOperation ->
                computeCow.globalOperations().operationCow(projectId, insertOperation));
  }

  /**
   * Helper method to build a new firewall rule that with network assosicated network.
   *
   * @param network the network to add the firewall rule to
   * @param firewall the firewall to be assosicated with a network
   * @return firewall rule object
   */
  @VisibleForTesting
  public static Firewall appendNetworkOnFirewall(Network network, Firewall firewall) {
    // Make a copy because the input is static and not thread safe.
    return firewall.clone().setNetwork(network.getSelfLink());
  }
}
