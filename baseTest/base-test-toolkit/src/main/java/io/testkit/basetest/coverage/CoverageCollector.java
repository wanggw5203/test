package io.testkit.basetest.coverage;

import java.util.Optional;

@FunctionalInterface
public interface CoverageCollector {
    Optional<CoverageArtifact> collect(CoverageRequest request);

    static CoverageCollector noop() {
        return request -> Optional.empty();
    }
}
