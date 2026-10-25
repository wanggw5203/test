package io.testkit.basetest.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/** Per-case LIFO compensation: register deletion/rollback immediately after data creation. */
public final class CleanupStack implements AutoCloseable {
  private final Deque<AutoCloseable> actions = new ArrayDeque<>();
  private boolean closed;

  public <T extends AutoCloseable> T add(T action) {
    if (closed) throw new IllegalStateException("Cleanup stack already closed");
    actions.push(java.util.Objects.requireNonNull(action));
    return action;
  }

  public void close() {
    if (closed) return;
    closed = true;
    RuntimeException failure = new IllegalStateException("Case cleanup failed");
    while (!actions.isEmpty()) {
      try {
        actions.pop().close();
      } catch (Exception | Error e) {
        failure.addSuppressed(e);
      }
    }
    if (failure.getSuppressed().length > 0) throw failure;
  }
}
