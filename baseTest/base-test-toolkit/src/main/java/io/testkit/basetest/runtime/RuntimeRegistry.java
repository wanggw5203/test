package io.testkit.basetest.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeRegistry {
  private static final AtomicReference<AutomationRuntime> CURRENT =
      new AtomicReference<>(AutomationRuntime.local());

  private RuntimeRegistry() {}

  private static final ThreadLocal<java.util.Deque<Scope>> SCOPES =
      ThreadLocal.withInitial(java.util.ArrayDeque::new);

  public static AutomationRuntime current() {
    Scope scoped = SCOPES.get().peek();
    return scoped == null ? CURRENT.get() : scoped.value;
  }

  public static Scope use(AutomationRuntime runtime) {
    return new Scope(Objects.requireNonNull(runtime));
  }

  public static final class Scope implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final AutomationRuntime value;
    private boolean closed;

    private Scope(AutomationRuntime value) {
      this.value = value;
      SCOPES.get().push(this);
    }

    public void close() {
      if (closed) return;
      if (Thread.currentThread() != owner || SCOPES.get().peek() != this)
        throw new IllegalStateException(
            "Runtime scopes must close in reverse order on their owning thread");
      SCOPES.get().pop();
      if (SCOPES.get().isEmpty()) SCOPES.remove();
      closed = true;
    }
  }

  public static void install(AutomationRuntime runtime) {
    CURRENT.set(Objects.requireNonNull(runtime));
  }

  public static void reset() {
    CURRENT.set(AutomationRuntime.local());
  }
}
