package io.testkit.basetest.enterprise;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable worker execution configuration shared by a validated method plan.
 *
 * @param run nonsecret execution identity
 * @param policy admission policy and permitted HTTP targets
 * @param shard deterministic method partition assignment
 */
public record EnterpriseExecution(RunMetadata run, ExecutionPolicy policy, Shard shard) {
  public EnterpriseExecution {
    Objects.requireNonNull(run, "run");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(shard, "shard");
  }

  /**
   * Reads the compatible {@code testkit.*} execution properties.
   *
   * <p>Client environment precedence is {@code test.env}, {@code TEST_ENV}, then {@code local}.
   * Optional {@code testkit.environment} must match that result. Explicitly configure one shared
   * run ID and distinct worker IDs when distributing a run. Defaults are for the local onboarding
   * example; enterprise teams should supply their own team ID and target policy.
   *
   * @throws IllegalArgumentException for conflicting environments, unsafe identifiers or invalid
   *     shard coordinates
   */
  public static EnterpriseExecution fromSystemProperties() {
    String environment =
        System.getProperty("test.env", System.getenv().getOrDefault("TEST_ENV", "local"));
    String explicit = System.getProperty("testkit.environment");
    if (explicit != null && !explicit.equals(environment)) {
      throw new IllegalArgumentException("Conflicting enterprise and client environments");
    }
    RunMetadata metadata =
        new RunMetadata(
            System.getProperty("testkit.runId", UUID.randomUUID().toString()),
            System.getProperty("testkit.workerId", "local"),
            environment,
            System.getProperty("testkit.team", "orders"));
    Shard assignment =
        new Shard(
            Integer.parseInt(System.getProperty("testkit.shard.index", "0")),
            Integer.parseInt(System.getProperty("testkit.shard.count", "1")));
    return new EnterpriseExecution(metadata, ExecutionPolicy.localSafe(), assignment);
  }
}
