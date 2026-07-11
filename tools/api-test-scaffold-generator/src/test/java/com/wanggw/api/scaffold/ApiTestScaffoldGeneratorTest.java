package com.wanggw.api.scaffold;

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
    void generatesPutStyleScaffoldAndExecutableizationPlan() throws Exception {
        Path spec = copyExampleSpec();
        Path project = temporaryDirectory.resolve("project");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();

        ApiTestScaffoldGenerator.GenerationResult result = generator.generate(spec, project, false);

        assertEquals("apicode_41032_5201_generated", result.getApiCode());
        assertEquals(8, result.getFiles().size());
        Path javaFile = project.resolve(
                "src/test/java/com/pupu/third/employment/apicode_41032_5201_generated/"
                        + "SupplierEntryProcessAuditSubmitGeneratedTests.java");
        String java = Files.readString(javaFile, StandardCharsets.UTF_8);
        assertTrue(java.contains("@PuTester(\"1000000\")"));
        assertTrue(java.contains("DataDriveUtil.loadTestData"));
        assertTrue(java.contains("SupplierEntryAuditSubmitDataPreparer"));

        Path suite = project.resolve(
                "src/test/resources/com/pupu/third/employment/apicode_41032_5201_generated/"
                        + "合作用工-提交审核生成样例.yaml");
        String suiteYaml = Files.readString(suite, StandardCharsets.UTF_8);
        assertTrue(suiteYaml.contains("testDataSuite:"));
        assertTrue(suiteYaml.contains("01_全职商家人资待提交人员提交审核成功.json"));

        Path request = suite.getParent().resolve(
                "01_全职商家人资待提交人员提交审核成功_req_pre.json5");
        String requestJson5 = Files.readString(request, StandardCharsets.UTF_8);
        assertTrue(requestJson5.contains("__DATA_REF:pending_entry.entry_id__"));
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
                "src/test/resources/com/pupu/third/employment/apicode_41032_5201_generated/"
                        + "01_全职商家人资待提交人员提交审核成功_req_pre.json5");
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
