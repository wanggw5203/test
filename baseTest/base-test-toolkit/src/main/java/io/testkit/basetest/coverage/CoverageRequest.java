package io.testkit.basetest.coverage;

import java.util.Set;

public record CoverageRequest(String runId, Set<String> caseResultIds, boolean resetAfterDump) {
    public CoverageRequest {
        caseResultIds = Set.copyOf(caseResultIds == null ? Set.of() : caseResultIds);
    }
}
