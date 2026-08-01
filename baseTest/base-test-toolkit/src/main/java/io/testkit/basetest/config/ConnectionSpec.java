package io.testkit.basetest.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A vendor-neutral connection description. Secrets are never rendered by toString(). */
public record ConnectionSpec(String name, Kind kind, Map<String, String> properties) {
    public enum Kind {
        DATABASE, CACHE, SEARCH, MESSAGE_QUEUE, OBJECT_STORAGE,
        RPC, CONFIG_CENTER, ANALYTICS, OTHER
    }

    public ConnectionSpec {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        kind = Objects.requireNonNullElse(kind, Kind.OTHER);
        properties = Map.copyOf(properties == null ? Map.of() : properties);
    }

    public String property(String key) {
        return properties.get(key);
    }

    public ConnectionSpec overlay(ConnectionSpec override) {
        if (override == null) return this;
        if (!name.equals(override.name) || kind != override.kind) {
            throw new IllegalArgumentException("Only matching connection specs can be merged");
        }
        Map<String, String> merged = new LinkedHashMap<>(properties);
        merged.putAll(override.properties);
        return new ConnectionSpec(name, kind, merged);
    }

    @Override
    public String toString() {
        return "ConnectionSpec[name=" + name + ", kind=" + kind + ", properties=<redacted>]";
    }
}
