package io.github.aitestflow.service;

import com.intellij.openapi.project.Project;
import io.github.aitestflow.enterprise.QualityGateService;
import io.github.aitestflow.enterprise.WorkspaceConfig;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.settings.AiTestFlowSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoldenDemoFlowTest {
    @TempDir Path root;

    @Test
    void defaultModelConfigurationReturnsGoldenResponsesWithoutAnyNetworkOrApiKey() throws Exception {
        AiTestFlowSettings settings = new AiTestFlowSettings();
        assertTrue(settings.data().demoMode);
        assertEquals(GoldenDemo.ID, settings.data().goldenDataset);
        settings.data().baseUrl = "not-a-valid-network-url";
        LlmClient client = offlineClient(settings);
        for (PipelineStage stage : PipelineStage.values()) {
            String first = client.execute(stage, "skill", "arbitrary input", httpEvidence(), List.of());
            String second = client.execute(stage, "skill", "different input", httpEvidence(), List.of());
            assertEquals(first, second, stage.name());
            assertTrue(first.contains("order-create-v1"), stage.name());
        }
    }

    @Test
    void httpAndDubboGoldenFlowsPassGatesAndGenerateCompleteProjectAssets() throws Exception {
        for (boolean dubbo : List.of(false, true)) {
            Path project = root.resolve(dubbo ? "dubbo" : "http");
            String evidence = dubbo ? "{\"protocol\":\"DUBBO\"}" : httpEvidence();
            LlmClient client = offlineClient(new AiTestFlowSettings());
            QualityGateService gates = new QualityGateService();
            WorkspaceConfig config = new WorkspaceConfig();
            String input = GoldenDemo.requirement();
            Files.createDirectories(project.resolve("quality-assets"));
            Files.writeString(project.resolve("pom.xml"), "<project><dependencies><dependency><groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId></dependency></dependencies></project>");
            Files.writeString(project.resolve("quality-assets/input.prd.md"), input);
            for (PipelineStage stage : List.of(PipelineStage.REQUIREMENT_TO_TAD,
                    PipelineStage.TAD_TO_TEST_CASES, PipelineStage.TEST_CASES_TO_API_SPEC,
                    PipelineStage.TEST_CASES_TO_WEB_ATC, PipelineStage.FEEDBACK)) {
                String output = client.execute(stage, "builtin-skill", input, evidence, List.of());
                var target = stage == PipelineStage.TEST_CASES_TO_API_SPEC
                        ? new QualityGateService.InterfaceTarget(dubbo ? "DUBBO" : "HTTP",
                        dubbo ? "DUBBO_METHOD" : "HTTP_PATH",
                        dubbo ? GoldenDemo.DUBBO_SELECTOR : GoldenDemo.HTTP_SELECTOR) : null;
                var gate = gates.validate(stage, output, config, target);
                assertTrue(gate.passed(), stage + ": " + gate.errors());
                Path outputFile = project.resolve("quality-assets").resolve(stage.outputFile());
                Files.writeString(outputFile, output);
                if (stage == PipelineStage.TEST_CASES_TO_API_SPEC) {
                    var result = new ScaffoldGenerationService(project)
                            .generate(project.relativize(outputFile).toString(), "quality-assets/" + PipelineStage.TAD_TO_TEST_CASES.outputFile());
                    assertTrue(result.generatedFiles().stream().anyMatch(path -> path.endsWith(".java")));
                    assertEquals(6, result.generatedFiles().stream().filter(path -> path.endsWith(".json5")).count());
                    assertTrue(Files.readString(project.resolve(result.manifestPath())).contains("GOLD-003"));
                    assertTrue(Files.readString(project.resolve(result.executableizationPlanPath())).contains("GOLD-001"));
                    if (dubbo) assertTrue(output.contains("basetest-dubbo"));
                    else {
                        assertTrue(output.contains("INVALID_QUANTITY"));
                        assertTrue(output.contains("SKU_REQUIRED"));
                        assertTrue(output.contains("201"));
                    }
                }
                if (stage == PipelineStage.FEEDBACK) {
                    assertTrue(output.contains("status: blocked"));
                    assertTrue(output.contains("not-run"));
                }
                input = output;
            }
        }
    }

    private static String httpEvidence() { return "{\"protocol\":\"HTTP\"}"; }

    private static LlmClient offlineClient(AiTestFlowSettings settings) {
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
                new Class<?>[]{Project.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getService") && args[0] == AiTestFlowSettings.class) return settings;
                    throw new AssertionError("Golden mode unexpectedly accessed project/model infrastructure: " + method);
                });
        return new LlmClient(project);
    }
}
