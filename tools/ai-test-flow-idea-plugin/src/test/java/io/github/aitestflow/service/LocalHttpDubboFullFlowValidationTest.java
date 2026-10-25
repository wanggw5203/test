package io.github.aitestflow.service;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import io.github.aitestflow.enterprise.AssetPlan;
import io.github.aitestflow.enterprise.QualityGateService;
import io.github.aitestflow.enterprise.WorkspaceConfig;
import io.github.aitestflow.initializer.ProjectInitializer;
import io.github.aitestflow.initializer.ProjectOptions;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.settings.AiTestFlowSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP and real cross-process Dubbo acceptance against the independently launched provider. */
class LocalHttpDubboFullFlowValidationTest {
    @TempDir Path temporary;
    private final WorkspaceConfig config = new WorkspaceConfig();
    private final QualityGateService gates = new QualityGateService();
    private final SkillRepository skills = new SkillRepository(null);
    private final HttpClient http = HttpClient.newHttpClient();
    private record Assets(Path project, AssetPlan plan, String protocol) {}

    @Test void bothProtocolsFromRequirementThroughGenerationAndRealExecution() throws Exception {
        String requested = System.getenv("AI_TEST_FLOW_RPC_VALIDATION_ROOT");
        boolean live = requested != null && !requested.isBlank();
        Path root = live ? Path.of(requested).toAbsolutePath().normalize() : temporary.resolve("flow");
        String requirement = live ? Files.readString(root.resolve("requirements/order-service.prd.md")) : GoldenDemo.requirement();
        String subdirectory = System.getenv("AI_TEST_FLOW_RPC_OUTPUT_SUBDIR");
        Path projectBase = subdirectory == null || subdirectory.isBlank() ? root : root.resolve(subdirectory).normalize();
        assertTrue(projectBase.startsWith(root), "Recheck projects must stay inside the validation workspace");
        JsonObject providerState = live ? JsonParser.parseString(Files.readString(root.resolve("runtime/ready.json"))).getAsJsonObject() : null;
        boolean configOnly = Boolean.parseBoolean(System.getenv("AI_TEST_FLOW_RPC_CONFIG_ONLY"));
        LlmClient model = client();
        List<Assets> assets = new ArrayList<>();
        for (var type : List.of(ProjectOptions.Type.HTTP, ProjectOptions.Type.DUBBO)) {
            Path project = projectBase.resolve(type.name().toLowerCase(java.util.Locale.ROOT) + "-tests");
            boolean newlyCreated = !Files.exists(project.resolve("pom.xml"));
            if (newlyCreated) new ProjectInitializer().initialize(project,
                    new ProjectOptions(type, "io.testkit.validation", "local-order-" + type.name().toLowerCase(java.util.Locale.ROOT),
                            "validation.order." + type.name().toLowerCase(java.util.Locale.ROOT), "1.1.0-SNAPSHOT"));
            if (newlyCreated && live && configOnly) {
                Path settings = project.resolve("src/main/resources/environments/local.properties");
                String configured = Files.readString(settings)
                        .replace("api.baseUrl=http://127.0.0.1:8080/", "api.baseUrl=" + providerState.get("httpBaseUrl").getAsString())
                        .replace("dubbo.directUrl=\n", "dubbo.directUrl=" + providerState.get("dubboDirectUrl").getAsString() + "\n")
                        .replace("dubbo.registry=\n", "dubbo.registry=N/A\n");
                Files.writeString(settings, configured);
            }
            AutomationProjectCheck.verify(project);
            Path source = project.resolve("quality-assets/inputs/order-service.prd.md");
            save(source, requirement);
            AssetPlan plan = AssetPlan.forSource(project, source, config);
            String evidence = "{\"protocol\":\"" + type + "\",\"source\":\"local typed service source and explicit endpoint contract\"}";
            String tad = stage(project, plan, model, PipelineStage.REQUIREMENT_TO_TAD, requirement, evidence, type);
            String cases = stage(project, plan, model, PipelineStage.TAD_TO_TEST_CASES, tad, evidence, type);
            stage(project, plan, model, PipelineStage.TEST_CASES_TO_API_SPEC, cases, evidence, type);
            var result = new ScaffoldGenerationService(project).generate(plan.apiSpecPath(), plan.testcasePath());
            assertEquals(6, result.generatedFiles().stream().filter(path -> path.endsWith(".json5")).count());
            stage(project, plan, model, PipelineStage.TEST_CASES_TO_WEB_ATC, cases, evidence, type);
            stage(project, plan, model, PipelineStage.FEEDBACK, "No execution yet", evidence, type);
            if (live) save(project.resolve("quality-assets/evidence/provider-source.json"), json(Map.of(
                    "interface", "demo.golden.OrderService#create(java.util.Map)",
                    "source", project.relativize(root.resolve("service/src/main/java/demo/golden/OrderService.java")).toString().replace('\\','/'),
                    "sha256", ArtifactStore.sha256(Files.readAllBytes(root.resolve("service/src/main/java/demo/golden/OrderService.java"))),
                    "discovery", "explicit source evidence, not an automatic PSI discovery claim")));
            assets.add(new Assets(project, plan, type.name()));
        }
        if (!live) return; // Ordinary unit suite remains offline and does not require a running RPC provider.

        JsonObject ready = JsonParser.parseString(Files.readString(root.resolve("runtime/ready.json"))).getAsJsonObject();
        String base = ready.get("httpBaseUrl").getAsString();
        String rpc = ready.get("dubboDirectUrl").getAsString();
        assertNotEquals(ProcessHandle.current().pid(), ready.get("pid").getAsLong());
        var health = http.send(HttpRequest.newBuilder(URI.create(base + "health")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        String run = "run-" + Instant.now().toEpochMilli();
        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("scope", "Standalone provider, real HTTP and Dubbo TCP, separate JVMs; AI outputs remain GoldenDemo fixtures");
        summary.put("provider", ready);
        summary.put("startedAt", Instant.now().toString());
        summary.put("projects", assets.stream().map(asset -> root.relativize(asset.project).toString().replace('\\','/')).toList());
        summary.put("addressConfiguration", configOnly ? "environment files; no address -D arguments" : "explicit address overrides");
        List<Map<String,Object>> results = new ArrayList<>();
        try {
            fault(base, false);
            for (Assets asset : assets) results.add(runMaven(asset, run, "baseline", base, rpc, false));
            fault(base, true);
            for (Assets asset : assets) results.add(runMaven(asset, run, "fault-injection", base, rpc, true));
            fault(base, false);
            for (Assets asset : assets) results.add(runMaven(asset, run, "recovered", base, rpc, false));
        } finally { fault(base, false); }

        for (Assets asset : assets) {
            String actual = Files.readString(asset.project.resolve("quality-assets/evidence/" + run + "/recovered-results.jsonl"));
            String generatedFeedback = model.execute(PipelineStage.FEEDBACK, skills.load("close-atc-feedback-loop"), actual,
                    "Real local " + asset.protocol + " execution evidence:\n" + actual, List.of());
            save(asset.project.resolve("quality-assets/evidence/" + run + "/model-feedback-after-execution.yaml"), generatedFeedback);
            assertTrue(generatedFeedback.contains("not-run"), "Default model remains a fixed fixture, not evidence analysis");
            Map<String,Object> feedback = new LinkedHashMap<>();
            feedback.put("protocol", asset.protocol);
            feedback.put("status", "passed");
            feedback.put("scope", "local actual protocol execution only");
            feedback.put("sourceCaseIds", List.of("GOLD-001", "GOLD-002", "GOLD-003"));
            feedback.put("evidence", "quality-assets/evidence/" + run + "/recovered-results.jsonl");
            feedback.put("producer", "deterministic acceptance harness reading actual reports, NOT AI or plugin feedback stage");
            feedback.put("executions", results.stream().filter(row -> row.get("protocol").equals(asset.protocol)).toList());
            save(asset.project.resolve("quality-assets/evidence/" + run + "/execution-feedback.yaml"), new Yaml().dump(Map.of("feedback",feedback)));
        }
        String audit = Files.readString(root.resolve("runtime/service-requests.jsonl"));
        assertTrue(audit.contains("\"protocol\":\"DUBBO\""));
        assertTrue(audit.contains("BUG_ZERO_ACCEPTED"));
        Path evidenceRoot = root.resolve("quality-assets/evidence/" + run);
        save(evidenceRoot.resolve("provider-audit.jsonl"), audit);
        summary.put("results", results);
        summary.put("overallStatus", "BOTH_PROTOCOLS_EXECUTED_FAULT_DETECTED_AND_RECOVERED; GOLDEN_AI_FEEDBACK_GAP_REMAINS");
        summary.put("limitations", List.of("No real model semantic analysis", "Default GoldenDemo feedback remains not-run even with actual logs",
                "No IDE GUI/VFS click-through validation", "Local designed service, not company backend", "No registry or database integration"));
        summary.put("finishedAt", Instant.now().toString());
        save(evidenceRoot.resolve("full-flow-report.json"), json(summary));
    }

    private Map<String,Object> runMaven(Assets asset, String run, String phase, String base, String rpc, boolean expectFailure) throws Exception {
        Path directory = asset.project.resolve("quality-assets/evidence/" + run);
        Files.createDirectories(directory);
        String resultPath = "quality-assets/evidence/" + run + "/" + phase + "-results.jsonl";
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) command.addAll(List.of("cmd.exe", "/d", "/c", "mvn"));
        else command.add("mvn");
        command.addAll(List.of("-B", "-ntp", "test", "-Dtestkit.report.file=" + resultPath));
        if (!Boolean.parseBoolean(System.getenv("AI_TEST_FLOW_RPC_CONFIG_ONLY")))
            command.addAll(List.of("-Dapi.baseUrl=" + base, "-Ddubbo.directUrl=" + rpc, "-Ddubbo.registry=N/A"));
        Process process = new ProcessBuilder(command).directory(asset.project.toFile()).redirectErrorStream(true)
                .redirectOutput(directory.resolve(phase + "-maven.log").toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.descendants().forEach(child -> child.destroyForcibly()); process.destroyForcibly();
            fail("Maven timed out for " + asset.protocol + "/" + phase);
        }
        save(directory.resolve(phase + "-command.json"), json(Map.of("command",command,"exitCode",process.exitValue())));
        List<JsonObject> records = new ArrayList<>();
        Path report = asset.project.resolve(resultPath);
        if (Files.exists(report)) for (String line : Files.readAllLines(report)) {
            JsonObject row = JsonParser.parseString(line).getAsJsonObject();
            if (row.has("type") && row.get("type").getAsString().equals("test")) records.add(row);
        }
        long passed = records.stream().filter(row -> row.get("status").getAsString().equals("PASSED")).count();
        long failed = records.stream().filter(row -> row.get("status").getAsString().equals("FAILED")).count();
        if (expectFailure) {
            assertNotEquals(0, process.exitValue(), "Fault must be detected: " + asset.protocol);
            assertEquals(1, failed, "Only zero-quantity scenario must fail; see " + directory);
            assertTrue(records.stream().anyMatch(row -> row.get("name").getAsString().startsWith("GOLD-002 / ") && row.get("status").getAsString().equals("FAILED")));
        } else assertEquals(0, process.exitValue(), "Read Maven log: " + directory.resolve(phase + "-maven.log"));
        assertEquals(4, records.size());
        assertEquals(expectFailure ? 3 : 4, passed);
        Path xml = asset.project.resolve("target/surefire-reports/TEST-TestSuite.xml");
        if (Files.exists(xml)) Files.copy(xml,directory.resolve(phase + "-testng.xml"));
        return Map.of("protocol",asset.protocol,"phase",phase,"exitCode",process.exitValue(),"passed",passed,"failed",failed,"evidence",resultPath);
    }

    private void fault(String base, boolean enabled) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "admin/fault"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"enabled\":" + enabled + "}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());
    }
    private String stage(Path project, AssetPlan plan, LlmClient model, PipelineStage stage, String input, String evidence, ProjectOptions.Type type) throws Exception {
        String output = model.execute(stage, skills.load(stage.skillName()), input, evidence, List.of());
        var target = stage == PipelineStage.TEST_CASES_TO_API_SPEC ? new QualityGateService.InterfaceTarget(type.name(),
                type == ProjectOptions.Type.HTTP ? "HTTP_PATH" : "DUBBO_METHOD", type == ProjectOptions.Type.HTTP ? GoldenDemo.HTTP_SELECTOR : GoldenDemo.DUBBO_SELECTOR,
                QualityGateService.extractCaseIds(input), List.of()) : null;
        var gate = gates.validate(stage, output, config, target);
        assertTrue(gate.passed(), stage + ": " + gate.errors());
        save(project.resolve(plan.pathFor(stage)), ArtifactStore.clean(output));
        return output;
    }
    private static void save(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) {
            String existing = Files.readString(file);
            if (file.toString().endsWith(".json")) assertEquals(JsonParser.parseString(content), JsonParser.parseString(existing), "Changed JSON validation asset: " + file);
            else assertEquals(content,existing,"Refusing to overwrite changed validation asset: " + file);
            return;
        }
        Files.writeString(file,content,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    private static String json(Object value) { return new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"; }
    private static LlmClient client() {
        AiTestFlowSettings settings = new AiTestFlowSettings();
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),new Class<?>[]{Project.class},(proxy,method,args) -> {
            if (method.getName().equals("getService") && args[0] == AiTestFlowSettings.class) return settings;
            throw new AssertionError("IDE access outside service-layer acceptance scope: " + method);
        });
        return new LlmClient(project);
    }
}
