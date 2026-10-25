package io.github.aitestflow.discovery;

import java.util.List;
import java.util.Locale;

/** HTTP-specific contract resolved by a discovery provider. */
public record HttpEndpoint(String method, String path, List<String> consumes, List<String> produces) {
    public HttpEndpoint {
        method = requireText(method, "method").toUpperCase(Locale.ROOT);
        path = normalizePath(path);
        consumes = consumes == null ? List.of() : List.copyOf(consumes);
        produces = produces == null ? List.of() : List.copyOf(produces);
    }

    public String selectorValue() {
        return method + " " + path;
    }

    private static String normalizePath(String value) {
        String path = requireText(value, "path");
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String requireText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
