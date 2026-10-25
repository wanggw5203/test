package io.github.aitestflow.service;

import io.github.aitestflow.initializer.ProjectInitializer;
import io.github.aitestflow.initializer.ProjectOptions;
import io.github.aitestflow.model.PipelineStage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InitializedProjectFlowTest {
    @TempDir Path root;

    @Test void initializationAndInterfaceGenerationAreSeparateButComposeWithoutChangingProjectInfrastructure() throws Exception {
        for (var type : java.util.List.of(ProjectOptions.Type.HTTP, ProjectOptions.Type.DUBBO)) {
            Path project = root.resolve(type.name());
            new ProjectInitializer().initialize(project, new ProjectOptions(type, "com.demo", "demo", "com.demo.automation", "1.1.0-SNAPSHOT"));
            String pom = Files.readString(project.resolve("pom.xml"));
            String bootstrap = Files.readString(project.resolve("src/main/java/com/demo/automation/bootstrap/ProjectBootstrap.java"));
            AutomationProjectCheck.verify(project);
            String evidence = "{\"protocol\":\"" + type + "\"}";
            Files.createDirectories(project.resolve("quality-assets/testcases"));
            Files.createDirectories(project.resolve("quality-assets/automation"));
            Files.writeString(project.resolve("quality-assets/testcases/cases.yaml"), GoldenDemo.generate(PipelineStage.TAD_TO_TEST_CASES, evidence));
            Files.writeString(project.resolve("quality-assets/automation/spec.yaml"), GoldenDemo.generate(PipelineStage.TEST_CASES_TO_API_SPEC, evidence));
            var result = new ScaffoldGenerationService(project).generate("quality-assets/automation/spec.yaml", "quality-assets/testcases/cases.yaml");
            assertEquals(6, result.generatedFiles().stream().filter(name -> name.endsWith(".json5")).count());
            assertTrue(result.generatedFiles().stream().anyMatch(name -> name.startsWith("src/main/java/") && name.endsWith("Api.java")));
            assertEquals(pom, Files.readString(project.resolve("pom.xml")));
            assertEquals(bootstrap, Files.readString(project.resolve("src/main/java/com/demo/automation/bootstrap/ProjectBootstrap.java")));
            assertEquals(result.generatedFiles(), new ScaffoldGenerationService(project)
                    .generate("quality-assets/automation/spec.yaml", "quality-assets/testcases/cases.yaml").generatedFiles());
        }
    }
}
