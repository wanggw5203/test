package io.testkit.basetest.enterprise;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable admission rules evaluated before a business test invocation.
 *
 * <p>Environment access is explicit; an empty service set permits all logical service names. HTTP
 * origins are independently denied unless allowed in {@link TargetPolicy}. This policy is not a
 * replacement for service-side IAM or database, message-broker and RPC permissions.
 *
 * @param environments permitted environment identifiers
 * @param services permitted service identifiers; empty means unrestricted names
 * @param destructiveAllowed whether destructive test definitions may run
 * @param targets allowed HTTP origins by environment and service
 */
public record ExecutionPolicy(
    Set<String> environments,
    Set<String> services,
    boolean destructiveAllowed,
    TargetPolicy targets) {
  public ExecutionPolicy {
    environments = Set.copyOf(environments);
    services = Set.copyOf(services);
    environments.forEach(environment -> RunMetadata.token(environment, "environment"));
    services.forEach(service -> RunMetadata.token(service, "service"));
    Objects.requireNonNull(targets, "targets");
  }

  /** Creates admission rules with all HTTP origins denied by default. */
  public ExecutionPolicy(
      Set<String> environments, Set<String> services, boolean destructiveAllowed) {
    this(environments, services, destructiveAllowed, TargetPolicy.denyAll());
  }

  /**
   * Returns local/dev/qa admission defaults with destructive cases and HTTP origins denied. The
   * name is retained for compatibility; it does not mean that dev and qa targets have been
   * configured or approved automatically.
   */
  public static ExecutionPolicy localSafe() {
    return new ExecutionPolicy(Set.of("local", "dev", "qa"), Set.of(), false);
  }

  /** Rejects environment, ownership, service or destructive-operation violations. */
  public void validate(RunMetadata run, CaseDefinition definition) {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(definition, "definition");
    if (!environments.contains(run.environment())) {
      throw new IllegalArgumentException("Environment denied by enterprise policy");
    }
    if (!run.team().equals(definition.team())) {
      throw new IllegalArgumentException("Case team differs from execution team");
    }
    if (!services.isEmpty() && !services.contains(definition.service())) {
      throw new IllegalArgumentException("Service denied by enterprise policy");
    }
    if (definition.destructive() && !destructiveAllowed) {
      throw new IllegalArgumentException("Destructive case denied by enterprise policy");
    }
  }
}
