package io.testkit.basetest.data;

import java.util.Optional;

public final class CaseContextHolder {
    private static final ThreadLocal<CaseContext> CURRENT = new ThreadLocal<>();

    private CaseContextHolder() {
    }

    public static void set(CaseContext context) { CURRENT.set(context); }
    public static Optional<CaseContext> current() { return Optional.ofNullable(CURRENT.get()); }
    public static void clear() { CURRENT.remove(); }
}
