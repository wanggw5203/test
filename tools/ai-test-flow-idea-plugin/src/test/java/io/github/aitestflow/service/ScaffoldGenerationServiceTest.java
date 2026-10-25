package io.github.aitestflow.service;

import io.github.aitestflow.model.PipelineStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldGenerationServiceTest {
    @TempDir Path projectRoot;

    @Test
    void generatesAndVerifiesHttpAssetsInsideProject() throws Exception {
        ScaffoldGenerationService.Result result = generate(
                "{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/orders\"}");

        assertTrue(Files.isRegularFile(projectRoot.resolve(result.manifestPath())));
        assertTrue(Files.isRegularFile(projectRoot.resolve(result.executableizationPlanPath())));
        assertTrue(result.generatedFiles().stream().allMatch(path -> Files.exists(projectRoot.resolve(path))));
        assertTrue(result.generatedFiles().stream().anyMatch(path -> path.endsWith("ApiTest.java")));
        assertFalse(result.generatedFiles().stream().anyMatch(path -> Path.of(path).isAbsolute()));
    }

    @Test
    void generatesDubboAssetsAndProtectsManualChanges() throws Exception {
        String evidence = "{\"protocol\":\"DUBBO\",\"selectorKind\":\"DUBBO_METHOD\","
                + "\"value\":\"com.demo.OrderService#query(java.util.Map)\"}";
        ScaffoldGenerationService.Result first = generate(evidence);
        Path javaFile = first.generatedFiles().stream().filter(path -> path.endsWith(".java"))
                .map(projectRoot::resolve).findFirst().orElseThrow();
        String generated = Files.readString(javaFile);
        assertTrue(generated.contains("DataDriveUtil.loadTestData"));
        assertTrue(generated.contains("CaseContext context"));

        Path spec = projectRoot.resolve("quality-assets/automation/demo.generation-spec.yaml");
        ScaffoldGenerationService service = new ScaffoldGenerationService(projectRoot);
        ScaffoldGenerationService.Result repeated = service.generate(projectRoot.relativize(spec).toString());
        assertEquals(first.generatedFiles(), repeated.generatedFiles());
        Files.writeString(javaFile, generated + "\n// team-owned edit\n");

        assertThrows(IOException.class,
                () -> service.generate(projectRoot.relativize(spec).toString()));
        assertTrue(Files.readString(javaFile).contains("team-owned edit"));
    }

    @Test void upstreamIdsAreVerifiedBeforeGeneratingAndBuildIsPreserved() throws Exception {
        var first = generate("{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/orders\"}");
        String build = Files.readString(projectRoot.resolve("pom.xml"));
        Files.writeString(projectRoot.resolve("cases.yaml"), "TestCases:\n  - caseId: WRONG-ID\n");
        var service = new ScaffoldGenerationService(projectRoot);
        assertThrows(IOException.class, () -> service.generate("quality-assets/automation/demo.generation-spec.yaml", "cases.yaml"));
        Files.writeString(projectRoot.resolve("cases.yaml"), "TestCases:\n  - caseId: CASE-001\n");
        assertEquals(first.generatedFiles(), service.generate("quality-assets/automation/demo.generation-spec.yaml", "cases.yaml").generatedFiles());
        assertEquals(build, Files.readString(projectRoot.resolve("pom.xml")));
        assertTrue(first.generatedFiles().stream().anyMatch(path -> path.startsWith("src/main/java/") && path.endsWith("Api.java")));
        assertFalse(first.generatedFiles().stream().anyMatch(path -> path.endsWith("pom.xml")));
    }

    private ScaffoldGenerationService.Result generate(String evidence) throws Exception {
        Files.writeString(projectRoot.resolve("pom.xml"), "<project><dependencies><dependency><groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId></dependency></dependencies></project>");
        String cases = "TestCases:\n  - caseId: CASE-001\n";
        String spec = DemoGenerator.generate(PipelineStage.TEST_CASES_TO_API_SPEC, cases, evidence);
        Path path = projectRoot.resolve("quality-assets/automation/demo.generation-spec.yaml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, spec);
        return new ScaffoldGenerationService(projectRoot)
                .generate(projectRoot.relativize(path).toString());
    }

    @Test void changedSpecificationCannotOverwriteAnExistingTeamClass() throws Exception {
        String evidence = "{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/orders\"}";
        var first = generate(evidence);
        Path spec = projectRoot.resolve("quality-assets/automation/demo.generation-spec.yaml");
        Path existing = projectRoot.resolve(first.generatedFiles().stream()
                .filter(path -> path.endsWith(".java")).findFirst().orElseThrow());
        Path teamClass = existing.resolveSibling("TeamOwnedTest.java");
        Files.writeString(teamClass, "// team-owned source");
        String oldSpec = Files.readString(spec);
        String changed = oldSpec.replaceFirst("className: [^\\r\\n]+", "className: TeamOwnedTest");
        Files.writeString(spec, changed);
        assertThrows(IOException.class, () -> new ScaffoldGenerationService(projectRoot)
                .generate(projectRoot.relativize(spec).toString()));
        assertEquals("// team-owned source", Files.readString(teamClass));
    }

    @Test void reformattedSpecificationAndLegacyManifestReuseExistingUnmodifiedAssets() throws Exception {
        var first = generate("{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/orders\"}");
        Path spec = projectRoot.resolve("quality-assets/automation/demo.generation-spec.yaml");
        Path manifest = projectRoot.resolve(first.manifestPath());
        String legacy = Files.readString(manifest).replaceAll("(?m)^sourceSpecSemanticSha256:.*\\R", "");
        Files.writeString(manifest, legacy);
        String source = Files.readString(spec).replace("schemaVersion: 2\n", "");
        Files.writeString(spec, "# reformatted only\r\n" + source.replace("\n", "\r\n") + "schemaVersion: 2\r\n");
        var repeat = new ScaffoldGenerationService(projectRoot).generate(projectRoot.relativize(spec).toString());
        assertEquals(first.generatedFiles(), repeat.generatedFiles());
        assertEquals(legacy, Files.readString(manifest), "Reuse must not rewrite the existing manifest");
    }
}
