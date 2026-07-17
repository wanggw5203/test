package io.testkit.basetest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ReturnValueContext {
    private static final ThreadLocal<Map<String, Object>> VALUES =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private ReturnValueContext() {
    }

    public static void put(String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        VALUES.get().put(name, value);
    }

    public static Optional<Object> get(String name) {
        return Optional.ofNullable(VALUES.get().get(name));
    }

    public static <T> Optional<T> get(String name, Class<T> type) {
        Object value = VALUES.get().get(name);
        return value == null ? Optional.empty() : Optional.of(type.cast(value));
    }

    public static Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(VALUES.get()));
    }

    public static void clear() {
        VALUES.remove();
    }
}
