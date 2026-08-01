package io.testkit.basetest.coverage;

import java.nio.file.Path;
import java.util.Map;

public record CoverageArtifact(Path file, Map<String, Object> metadata) {
    public CoverageArtifact {
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
