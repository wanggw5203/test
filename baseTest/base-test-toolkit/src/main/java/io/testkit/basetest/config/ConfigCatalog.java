package io.testkit.basetest.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Merges remote and local connection configuration without knowing any vendor platform. */
public final class ConfigCatalog {
    private final Map<String, ConnectionSpec> connections;

    private ConfigCatalog(Map<String, ConnectionSpec> connections) {
        this.connections = Map.copyOf(connections);
    }

    public static ConfigCatalog merge(Collection<ConnectionSpec> remote,
                                      Collection<ConnectionSpec> local,
                                      boolean localFirst) {
        Map<String, ConnectionSpec> merged = new LinkedHashMap<>();
        putAll(merged, localFirst ? remote : local);
        putAll(merged, localFirst ? local : remote);
        return new ConfigCatalog(merged);
    }

    private static void putAll(Map<String, ConnectionSpec> target,
                               Collection<ConnectionSpec> values) {
        if (values == null) return;
        for (ConnectionSpec value : values) {
            target.merge(key(value), value, ConnectionSpec::overlay);
        }
    }

    private static String key(ConnectionSpec spec) {
        return spec.kind() + ":" + spec.name();
    }

    public Optional<ConnectionSpec> find(ConnectionSpec.Kind kind, String name) {
        return Optional.ofNullable(connections.get(kind + ":" + name));
    }

    public ConnectionSpec require(ConnectionSpec.Kind kind, String name) {
        return find(kind, name).orElseThrow(() ->
                new IllegalArgumentException("Connection is not configured: " + kind + "/" + name));
    }

    public Collection<ConnectionSpec> all() {
        return connections.values();
    }
}
