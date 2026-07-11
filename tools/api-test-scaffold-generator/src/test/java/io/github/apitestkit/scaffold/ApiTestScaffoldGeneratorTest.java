package io.github.apitestkit.scaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTestScaffoldGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesFrameworkNeutralScaffoldAndExecutableizationPlan() throws Exception {
        Path spec = copyExampleSpec();
        Path project = temporaryDirectory.resolve("project");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();

        ApiTestScaffoldGenerator.GenerationResult result = generator.generate(spec, project, false);

        assertEquals("api_record_create", result.getApiCode());
        assertEquals(8, result.getFiles().size());
        Path javaFile = project.resolve(
                "src/test/java/com/example/automation/api_record_create/"
                        + "RecordCreateGeneratedTests.java");
        String java = Files.readString(javaFile, StandardCharsets.UTF_8);
        assertTrue(java.contains("@TestOwner(\"automation\")"));
        assertTrue(java.contains("DataDriveUtil.loadTestData"));
        assertTrue(java.contains("RecordDataPreparer"));

        Path suite = project.resolve(
                "src/test/resources/com/example/automation/api_record_create/"
                        + "记录管理-创建记录生成样例.yaml");
        String suiteYaml = Files.readString(suite, StandardCharsets.UTF_8);
        assertTrue(suiteYaml.contains("testDataSuite:"));
        assertTrue(suiteYaml.contains("01_有效管理员创建记录成功.json"));

        Path request = suite.getParent().resolve(
                "01_有效管理员创建记录成功_req_staging.json5");
        String requestJson5 = Files.readString(request, StandardCharsets.UTF_8);
        assertTrue(requestJson5.contains("__DATA_REF:parent_record.id__"));
        assertTrue(requestJson5.contains("\"_enum\""));

        String plan = Files.readString(result.getExecutableizationPlan(), StandardCharsets.UTF_8);
        assertTrue(plan.contains("nextSkill: make-api-tests-executable"));
        assertTrue(plan.contains("runtime-business-data"));

        ApiTestScaffoldGenerator.ValidationReport report = generator.validate(
                project, result.getManifest(), true);
        assertTrue(report.isSuccess());
        assertTrue(report.getMissingFiles().isEmpty());
        assertTrue(report.getModifiedFiles().isEmpty());
    }

    @Test
    void refusesToOverwriteGeneratedFilesByDefault() throws Exception {
        Path spec = copyExampleSpec();
        Path project = temporaryDirectory.resolve("project-overwrite");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();
        generator.generate(spec, project, false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> generator.generate(spec, project, false));
        assertTrue(error.getMessage().contains("refusing to overwrite"));
    }

    @Test
    void relaxedValidationAllowsIntentionalSecondPassChanges() throws Exception {
        Path spec = copyExampleSpec();
        Path project = temporaryDirectory.resolve("project-second-pass");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();
        ApiTestScaffoldGenerator.GenerationResult result = generator.generate(spec, project, false);
        Path request = project.resolve(
                "src/test/resources/com/example/automation/api_record_create/"
                        + "01_有效管理员创建记录成功_req_staging.json5");
        Files.writeString(request, "\n// processed by make-api-tests-executable\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        ApiTestScaffoldGenerator.ValidationReport strict = generator.validate(
                project, result.getManifest(), true);
        assertFalse(strict.isSuccess());
        assertEquals(1, strict.getModifiedFiles().size());

        ApiTestScaffoldGenerator.ValidationReport relaxed = generator.validate(
                project, result.getManifest(), false);
        assertTrue(relaxed.isSuccess());
        assertEquals(1, relaxed.getModifiedFiles().size());
    }

    private Path copyExampleSpec() throws Exception {
        Path spec = temporaryDirectory.resolve("generation-spec.yaml");
        try (InputStream input = getClass().getResourceAsStream("/example-spec.yaml")) {
            if (input == null) {
                throw new IllegalStateException("example-spec.yaml not found");
            }
            Files.write(spec, input.readAllBytes());
        }
        return spec;
    }
}
