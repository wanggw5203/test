package io.testkit.basetest.enterprise;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Invocation-local business data namespace with explicit thread ownership.
 *
 * <p>Scopes must close on their creating thread in reverse nesting order. Async code must
 * explicitly capture and rebind a {@link Snapshot}; this context does not use inheritable thread
 * locals or automatically propagate to executors.
 */
public final class EnterpriseContext implements AutoCloseable {
  /** Immutable metadata safe to propagate to an approved asynchronous worker. */
  public record Snapshot(
      EnterpriseExecution execution, CaseDefinition definition, String invocationId) {
    public Snapshot {
      Objects.requireNonNull(execution, "execution");
      Objects.requireNonNull(definition, "definition");
      RunMetadata.token(invocationId, "invocationId");
    }

    /**
     * Returns a deterministic per-invocation data key. The approved prefix is followed by a hash of
     * run, worker, team, environment, service, case and invocation identities. This isolates names;
     * it does not reserve external resources or remove created business data automatically.
     */
    public String fixtureKey(String prefix) {
      RunMetadata.token(prefix, "fixture prefix");
      RunMetadata run = execution.run();
      String hash =
          Shard.digest(
              run.runId()
                  + "|"
                  + run.workerId()
                  + "|"
                  + run.team()
                  + "|"
                  + run.environment()
                  + "|"
                  + definition.service()
                  + "|"
                  + definition.id()
                  + "|"
                  + invocationId);
      return prefix + "-" + hash.substring(0, 32);
    }
  }

  private static final ThreadLocal<Deque<EnterpriseContext>> STACK =
      ThreadLocal.withInitial(ArrayDeque::new);
  private final Thread owner = Thread.currentThread();
  private final Snapshot snapshot;
  private boolean closed;

  private EnterpriseContext(Snapshot snapshot) {
    this.snapshot = snapshot;
    STACK.get().push(this);
  }

  /** Validates admission and binds a snapshot until this scope is closed. */
  public static EnterpriseContext use(Snapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    snapshot.execution().policy().validate(snapshot.execution().run(), snapshot.definition());
    return new EnterpriseContext(snapshot);
  }

  /** Returns the innermost bound invocation, or empty outside an invocation. */
  public static Optional<Snapshot> current() {
    Deque<EnterpriseContext> contexts = STACK.get();
    if (contexts.isEmpty()) {
      STACK.remove();
      return Optional.empty();
    }
    return Optional.of(contexts.peek().snapshot);
  }

  /** Unbinds this scope; repeat close is harmless after successful closure. */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    if (owner != Thread.currentThread() || STACK.get().peek() != this) {
      throw new IllegalStateException("Enterprise scopes require owner-thread LIFO close");
    }
    STACK.get().pop();
    if (STACK.get().isEmpty()) {
      STACK.remove();
    }
    closed = true;
  }
}
