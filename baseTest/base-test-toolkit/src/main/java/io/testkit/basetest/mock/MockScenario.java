package io.testkit.basetest.mock;

import java.util.List;

public record MockScenario(String id, MockScope scope, List<MockRule> rules) {
    public MockScenario {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        scope = scope == null ? MockScope.CASE : scope;
        rules = List.copyOf(rules == null ? List.of() : rules);
    }
}
