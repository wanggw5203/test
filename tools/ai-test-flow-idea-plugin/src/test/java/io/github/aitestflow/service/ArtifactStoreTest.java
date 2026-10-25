package io.github.aitestflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactStoreTest {
    @Test
    void removesMarkdownFenceFromMachineReadableArtifact() {
        assertEquals("TestCases:\n  - caseId: TC-1\n", ArtifactStore.clean("```yaml\nTestCases:\n  - caseId: TC-1\n```"));
    }

    @Test
    void preservesMarkdownDocument() {
        assertEquals("# TAD\n\n正文\n", ArtifactStore.clean("# TAD\n\n正文"));
    }

    @Test void normalizesWindowsAndMixedLineEndingsForIdeaDocuments() {
        String cleaned = ArtifactStore.clean("```yaml\r\nTestCases:\r\n  - caseId: TC-1\r```\r\n");
        assertEquals("TestCases:\n  - caseId: TC-1\n", cleaned);
        com.intellij.openapi.util.text.StringUtil.assertValidSeparators(cleaned);
    }
}
