package io.github.aitestflow.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional read-only regression replay using the project that reported the failure. */
class ExistingProjectReplayTest {
    @Test void actualKnowledgeProjectCanReuseItsExistingGoldenScaffoldWithoutChangingFiles() throws Exception {
        Path root = Path.of("../../demo/knowledge").toAbsolutePath().normalize();
        Path manifest = root.resolve(".api-test-generator/golden_order_http_v1/scaffold-manifest.yaml");
        assumeTrue(Files.isRegularFile(manifest));
        Path spec;
        try (var paths = Files.list(root.resolve("quality-assets/automation"))) {
            spec = paths.filter(path -> path.toString().endsWith(".generation-spec.yaml")).findFirst().orElseThrow();
        }
        Map<Path, String> before = new LinkedHashMap<>();
        try (var paths = Files.walk(root.resolve("src/test"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                before.put(path, ArtifactStore.sha256(Files.readAllBytes(path)));
        }
        String manifestBefore = Files.readString(manifest);
        assertThrows(java.io.IOException.class, () -> new ScaffoldGenerationService(root).generate(root.relativize(spec).toString()),
                "Legacy generic assets must not be silently replaced by the new framework skeleton");
        for (var item : before.entrySet()) assertEquals(item.getValue(), ArtifactStore.sha256(Files.readAllBytes(item.getKey())));
        assertEquals(manifestBefore, Files.readString(manifest));
    }
}
