package io.testkit.basetest.runtime;

import java.util.*;

/** Correlation scope propagated explicitly across SDK worker boundaries. */
public final class TraceContext implements AutoCloseable {
  public record Trace(String runId, String caseId) {}

  private static final ThreadLocal<Deque<TraceContext>> STACK =
      ThreadLocal.withInitial(ArrayDeque::new);
  private final Trace trace;
  private final Thread owner = Thread.currentThread();
  private boolean closed;

  private TraceContext(Trace trace) {
    this.trace = trace;
    STACK.get().push(this);
  }

  public static TraceContext use(Trace trace) {
    return new TraceContext(Objects.requireNonNull(trace));
  }

  public static Optional<Trace> current() {
    return Optional.ofNullable(STACK.get().peek()).map(scope -> scope.trace);
  }

  public void close() {
    if (closed) return;
    if (owner != Thread.currentThread() || STACK.get().peek() != this)
      throw new IllegalStateException("Trace owner-thread LIFO required");
    STACK.get().pop();
    if (STACK.get().isEmpty()) STACK.remove();
    closed = true;
  }
}
