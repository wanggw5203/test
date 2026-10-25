package io.github.aitestflow.enterprise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputAssetServiceNamingTest {
    @Test
    void placeholderDocumentsExpectedGitFirstLayout() {
        WorkspaceConfig config = new WorkspaceConfig();
        assertEquals("quality-assets/inputs", config.paths.inputs);
        assertEquals("quality-assets/evidence", config.paths.evidence);
    }
}
