package io.testkit.basetest.identity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Thread-isolated, nestable identity scope for parallel TestNG execution. */
public final class IdentityContext implements AutoCloseable {
    private static final ThreadLocal<Deque<UserIdentity>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private boolean closed;

    private IdentityContext(UserIdentity identity) {
        STACK.get().push(identity);
    }

    public static IdentityContext use(UserIdentity identity) {
        if (identity == null) throw new IllegalArgumentException("identity must not be null");
        return new IdentityContext(identity);
    }

    public static Optional<UserIdentity> current() {
        return Optional.ofNullable(STACK.get().peek());
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
        Deque<UserIdentity> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
        closed = true;
    }
}
