package io.testkit.basetest.runtime;

import java.time.Instant;

public record TestRunSummary(String runId, Instant startedAt, Instant endedAt,
                             int passed, int failed, int skipped) {
}
