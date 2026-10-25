package io.github.aitestflow.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AutomationProjectCheckTest {
    @TempDir Path root;

    @Test void emptyProjectIsRejectedWithoutInitializingAnything() throws Exception {
        assertThrows(IOException.class, () -> AutomationProjectCheck.verify(root));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void existingBuildIsNeverChangedAndContractIsReadOnly() throws Exception {
        String build = "<project><!-- team-owned --></project>";
        Files.writeString(root.resolve("pom.xml"), build);
        Files.createDirectories(root.resolve(".ai-test-flow"));
        Path contract = root.resolve(".ai-test-flow/automation-project.yaml");
        Files.writeString(contract, "schemaVersion: 1\nframework: io.testkit:base-test-toolkit\nframeworkScope: test\n");
        assertThrows(IOException.class, () -> AutomationProjectCheck.verify(root));
        String valid = Files.readString(contract).replace("frameworkScope: test", "frameworkScope: compile");
        Files.writeString(contract, valid);
        AutomationProjectCheck.verify(root);
        assertEquals(build, Files.readString(root.resolve("pom.xml")));
        assertEquals(valid, Files.readString(contract));
    }

    @Test void testOnlyFrameworkDependencyIsRejected() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project><dependencies><dependency><groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId><scope>test</scope></dependency></dependencies></project>");
        assertThrows(IOException.class, () -> AutomationProjectCheck.verify(root));
    }
}
