package io.testkit.basetest.identity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Thread-isolated, nestable identity scope for parallel TestNG execution. */
public final class IdentityContext implements AutoCloseable {
  private static final ThreadLocal<Deque<IdentityContext>> STACK =
      ThreadLocal.withInitial(ArrayDeque::new);
  private boolean closed;
  private final UserIdentity identity;
  private final Thread owner = Thread.currentThread();

  private IdentityContext(UserIdentity identity) {
    this.identity = identity;
    STACK.get().push(this);
  }

  public static IdentityContext use(UserIdentity identity) {
    if (identity == null) throw new IllegalArgumentException("identity must not be null");
    return new IdentityContext(identity);
  }

  public static Optional<UserIdentity> current() {
    IdentityContext scope = STACK.get().peek();
    return scope == null ? Optional.empty() : Optional.of(scope.identity);
  }

  public static UserIdentity requireCurrent() {
    return current().orElseThrow(() -> new IllegalStateException("No identity is active"));
  }

  public static void clear() {
    STACK.remove();
  }

  @Override
  public void close() {
    if (closed) return;
    Deque<IdentityContext> stack = STACK.get();
    if (Thread.currentThread() != owner || stack.peek() != this)
      throw new IllegalStateException(
          "Identity scopes must close in reverse order on their owning thread");
    stack.pop();
    if (stack.isEmpty()) STACK.remove();
    closed = true;
  }
}
