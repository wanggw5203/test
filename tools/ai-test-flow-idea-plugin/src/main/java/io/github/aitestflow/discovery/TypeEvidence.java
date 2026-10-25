package io.github.aitestflow.discovery;

import java.util.List;
import java.util.Objects;

/** A BO/VO/DTO (or related type) together with the source revision it was extracted from. */
public record TypeEvidence(
        TypeRole role,
        String fqcn,
        String sourcePath,
        String sha256,
        List<TypeFieldEvidence> fields
) {
    public TypeEvidence {
        role = Objects.requireNonNull(role, "role");
        fqcn = requireText(fqcn, "fqcn");
        sourcePath = text(sourcePath).replace('\\', '/');
        sha256 = text(sha256);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    private static String requireText(String value, String name) {
        String normalized = text(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
