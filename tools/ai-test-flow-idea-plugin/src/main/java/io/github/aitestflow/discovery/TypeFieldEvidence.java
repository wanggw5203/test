package io.github.aitestflow.discovery;

import java.util.List;
import java.util.Objects;

/** Field-level structure and validation constraints extracted from source code. */
public record TypeFieldEvidence(
        String name,
        String javaType,
        boolean required,
        List<String> constraints,
        String description
) {
    public TypeFieldEvidence {
        name = requireText(name, "name");
        javaType = requireText(javaType, "javaType");
        constraints = constraints == null
                ? List.of()
                : constraints.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
        description = description == null ? "" : description.trim();
    }

    private static String requireText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
