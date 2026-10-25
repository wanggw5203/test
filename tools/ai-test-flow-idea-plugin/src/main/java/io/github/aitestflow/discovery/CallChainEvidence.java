package io.github.aitestflow.discovery;

import java.util.Objects;

/** One ordered, source-backed node in the implementation chain. */
public record CallChainEvidence(
        int order,
        CallChainKind kind,
        JavaSymbolEvidence symbol,
        String relation
) {
    public CallChainEvidence {
        if (order < 1) {
            throw new IllegalArgumentException("order must be positive");
        }
        kind = Objects.requireNonNull(kind, "kind");
        symbol = Objects.requireNonNull(symbol, "symbol");
        relation = relation == null ? "" : relation.trim();
    }
}
