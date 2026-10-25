package io.github.aitestflow.discovery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral input for interface discovery.
 *
 * <p>The module and scopes are optional search boundaries. A future PSI provider can interpret
 * scopes as source roots/packages, while an OpenAPI or metadata provider can map them to its own
 * catalog namespaces.</p>
 */
public record EndpointQuery(
        EndpointProtocol protocol,
        EndpointSelectorKind selectorKind,
        String value,
        String module,
        List<String> scopes
) {
    public EndpointQuery {
        protocol = protocol == null ? EndpointProtocol.AUTO : protocol;
        selectorKind = Objects.requireNonNull(selectorKind, "selectorKind");
        value = requireText(value, "value");
        module = trimToEmpty(module);
        scopes = scopes == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(scopes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList()));
    }

    public static EndpointQuery of(EndpointProtocol protocol, EndpointSelectorKind selectorKind, String value) {
        return new EndpointQuery(protocol, selectorKind, value, "", List.of());
    }

    public static EndpointQuery httpPath(String path) {
        return of(EndpointProtocol.HTTP, EndpointSelectorKind.HTTP_PATH, path);
    }

    public static EndpointQuery dubboMethod(String method) {
        return of(EndpointProtocol.DUBBO, EndpointSelectorKind.DUBBO_METHOD, method);
    }

    public boolean hasModuleBoundary() {
        return !module.isEmpty();
    }

    public boolean hasScopeBoundary() {
        return !scopes.isEmpty();
    }

    private static String requireText(String value, String name) {
        String normalized = trimToEmpty(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
