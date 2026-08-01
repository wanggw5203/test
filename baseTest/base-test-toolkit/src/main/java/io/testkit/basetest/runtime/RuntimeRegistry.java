package io.testkit.basetest.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeRegistry {
    private static final AtomicReference<AutomationRuntime> CURRENT =
            new AtomicReference<>(AutomationRuntime.local());

    private RuntimeRegistry() {
    }

    public static AutomationRuntime current() {
        return CURRENT.get();
    }

    public static void install(AutomationRuntime runtime) {
        CURRENT.set(Objects.requireNonNull(runtime));
    }

    public static void reset() {
        CURRENT.set(AutomationRuntime.local());
    }
}
