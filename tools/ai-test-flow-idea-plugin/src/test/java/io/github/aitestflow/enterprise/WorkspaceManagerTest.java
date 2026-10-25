package io.github.aitestflow.enterprise;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceManagerTest {
    @Test void recoversBlankWorkspaceAndKeepsOldPartialConfigurations() throws Exception {
        assertNotNull(WorkspaceManager.parseConfiguration("").automation);
        assertNotNull(WorkspaceManager.parseConfiguration(" \n\t").paths);
        WorkspaceConfig partial = WorkspaceManager.parseConfiguration("{\"team\":\"custom-team\"}");
        assertEquals("custom-team", partial.team);
        assertNotNull(partial.gates);
        assertNotNull(partial.automation);
    }
    @Test void rejectsMalformedOrExplicitlyNullConfigurationsWithActionableMessage() {
        IOException error = assertThrows(IOException.class, () -> WorkspaceManager.parseConfiguration("{broken"));
        assertTrue(error.getMessage().contains("workspace.json"));
        assertThrows(IOException.class, () -> WorkspaceManager.parseConfiguration("null"));
    }
}
