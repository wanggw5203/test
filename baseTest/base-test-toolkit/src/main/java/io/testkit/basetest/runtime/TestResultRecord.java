package io.testkit.basetest.runtime;

import java.time.Instant;
import java.util.Map;

public record TestResultRecord(String runId, String caseId, String name, Status status,
                               Instant startedAt, long durationMillis, String error,
                               Map<String, Object> metadata) {
    public enum Status { PASSED, FAILED, SKIPPED }

    public TestResultRecord {
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
