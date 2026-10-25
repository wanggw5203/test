package io.github.aitestflow.initializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProjectInitializerTest {
    @TempDir Path root;
    private ProjectOptions options(ProjectOptions.Type type) {
        return new ProjectOptions(type, "com.demo", "demo-automation", "com.demo.automation", "1.1.0-SNAPSHOT");
    }
    @Test void allTypesCreateMainTestEnvironmentAndFrameworkContract() throws Exception {
        for (var type : ProjectOptions.Type.values()) {
            Path project = root.resolve(type.name());
            var created = new ProjectInitializer().initialize(project, options(type));
            assertTrue(created.size() >= 17);
            assertTrue(Files.readString(project.resolve("pom.xml")).contains("base-test-toolkit"));
            assertTrue(Files.exists(project.resolve("src/main/java/com/demo/automation/bootstrap/AutomationMain.java")));
            assertTrue(Files.exists(project.resolve("src/test/java/com/demo/automation/smoke/InitializationSmokeTest.java")));
            assertTrue(Files.readString(project.resolve(".ai-test-flow/automation-project.yaml")).contains("projectType: " + type));
            assertTrue(Files.exists(project.resolve("src/main/resources/META-INF/services/org.testng.ITestNGListener")));
            assertFalse(Files.exists(project.resolve(".git")));
            assertTrue(Files.readString(project.resolve("src/main/resources/environments/local.properties")).contains("testkit.log.bodies=true"));
            assertTrue(Files.readString(project.resolve("src/main/resources/environments/pre.properties")).contains("testkit.log.bodies=false"));
            if (type == ProjectOptions.Type.DUBBO) assertTrue(Files.readString(project.resolve("pom.xml")).contains("enterprise-adapters"));
            if (type == ProjectOptions.Type.SDK) assertTrue(Files.exists(project.resolve("src/main/java/com/demo/automation/sdk/SdkAdapter.java")));
        }
    }
    @Test void existingProjectIsNeverReinitialized() throws Exception {
        Path build = root.resolve("pom.xml");
        Files.writeString(build, "team-owned");
        assertThrows(IOException.class, () -> new ProjectInitializer().initialize(root, options(ProjectOptions.Type.HTTP)));
        assertEquals("team-owned", Files.readString(build));
        try (var files = Files.list(root)) { assertEquals(1, files.count()); }
    }
    @Test void collisionIsDetectedBeforeAnyWrites() throws Exception {
        Files.writeString(root.resolve("README.md"), "team-owned");
        assertThrows(IOException.class, () -> new ProjectInitializer().initialize(root, options(ProjectOptions.Type.SDK)));
        assertFalse(Files.exists(root.resolve("pom.xml")));
        assertEquals("team-owned", Files.readString(root.resolve("README.md")));
    }
    @Test void existingIgnoreRulesArePreserved() throws Exception {
        Files.writeString(root.resolve(".gitignore"), "team-private\n");
        new ProjectInitializer().initialize(root, options(ProjectOptions.Type.HTTP));
        assertEquals("team-private\n", Files.readString(root.resolve(".gitignore")));
    }
    @Test void previewIsPureAndTypesAreNotInvented() {
        var sdk = new ProjectInitializer().preview(options(ProjectOptions.Type.SDK));
        assertFalse(sdk.get("pom.xml").contains("enterprise-adapters"));
        assertFalse(Files.exists(root.resolve("pom.xml")));
    }
    @Test void invalidNamesAndInjectionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectOptions(ProjectOptions.Type.HTTP, "x", "../bad", "com.test", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new ProjectOptions(ProjectOptions.Type.HTTP, "x", "valid", "com.class", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new ProjectOptions(ProjectOptions.Type.HTTP, "x", "valid", "com.test", "1.0</version>"));
    }
}
