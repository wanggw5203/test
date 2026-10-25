package io.github.aitestflow.service;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.sun.net.httpserver.HttpServer;
import io.github.aitestflow.enterprise.AssetPlan;
import io.github.aitestflow.enterprise.QualityGateService;
import io.github.aitestflow.enterprise.WorkspaceConfig;
import io.github.aitestflow.initializer.ProjectInitializer;
import io.github.aitestflow.initializer.ProjectOptions;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.settings.AiTestFlowSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** One isolated, inspectable acceptance project. No IDEA GUI or remote model is impersonated. */
class RequirementProjectE2EValidationTest {
    @TempDir Path temporary;
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final QualityGateService gates = new QualityGateService();
    private final WorkspaceConfig config = new WorkspaceConfig();
    private final SkillRepository skills = new SkillRepository(null);

    @Test void requirementToExecutionAndFeedback() throws Exception {
        String output = System.getenv("AI_TEST_FLOW_VALIDATION_ROOT");
        boolean execute = output != null && !output.isBlank();
        Path root = execute ? Path.of(output).toAbsolutePath().normalize() : temporary.resolve("project");
        // Never destroy or repurpose an existing project during validation.
        assertFalse(Files.exists(root.resolve("pom.xml")), "Use a new isolated validation directory, not an existing initialized project");
        new ProjectInitializer().initialize(root, new ProjectOptions(ProjectOptions.Type.HTTP,
                "io.testkit.validation", "requirement-order-flow", "validation.order", "1.1.0-SNAPSHOT"));
        Path source = root.resolve("quality-assets/inputs/order-create.prd.md");
        if (!Files.exists(source)) save(root, "quality-assets/inputs/order-create.prd.md", GoldenDemo.requirement());
        String prd = Files.readString(source);
        AssetPlan plan = AssetPlan.forSource(root, source, config);
        save(root, ".ai-test-flow/workspace.json", json(config));
        report.put("startedAt", Instant.now().toString());
        report.put("projectRoot", root.toString());
        report.put("generationMode", "GOLDEN_DEMO: order-create-v1; no remote AI model");
        report.put("writeMode", "filesystem test harness; IDEA UI/VFS not exercised");
        report.put("requirement", Map.of("path", root.relativize(source).toString(), "sha256", ArtifactStore.sha256(prd)));
        check("project-initialization", true, "Independent initializer; framework compile dependency, bootstrap, main/test, YAML/JSON smoke test");
        LlmClient client = client();
        String evidence = "{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"POST /demo/orders\"}";
        String tad = stage(root, plan, client, PipelineStage.REQUIREMENT_TO_TAD, prd, evidence);
        String cases = stage(root, plan, client, PipelineStage.TAD_TO_TEST_CASES, tad, evidence);
        String spec = stage(root, plan, client, PipelineStage.TEST_CASES_TO_API_SPEC, cases, evidence);
        var generated = new ScaffoldGenerationService(root).generate(plan.apiSpecPath(), plan.testcasePath());
        report.put("scaffold", generated);
        check("framework-scaffold", true, generated.generatedFiles().size() + " generated files; main API/DataBuilder, test BaseTest/DataDriveUtil and YAML/JSON5");
        assertEquals(generated.generatedFiles(), new ScaffoldGenerationService(root).generate(plan.apiSpecPath(), plan.testcasePath()).generatedFiles());
        check("repeat-generation", true, "Identical files reused without modifying POM or bootstrap");
        Path mainApi = generated.generatedFiles().stream().filter(name -> name.startsWith("src/main/") && name.endsWith("Api.java")).map(root::resolve).findFirst().orElseThrow();
        String original = Files.readString(mainApi);
        try {
            Files.writeString(mainApi, original + "\n// intentional validation probe\n");
            assertThrows(java.io.IOException.class, () -> new ScaffoldGenerationService(root).generate(plan.apiSpecPath(), plan.testcasePath()));
            check("manual-edit-protection", true, "Regeneration refused after an intentional edit; original restored by this test");
        } finally { Files.writeString(mainApi, original); }
        save(root, "quality-assets/evidence/unknown-case.yaml", "TestCases:\n  - caseId: UNKNOWN-CASE\n");
        assertThrows(java.io.IOException.class, () -> new ScaffoldGenerationService(root).generate(plan.apiSpecPath(), "quality-assets/evidence/unknown-case.yaml"));
        check("upstream-case-protection", true, "Unknown sourceCaseId rejected before code generation");
        stage(root, plan, client, PipelineStage.TEST_CASES_TO_WEB_ATC, cases, evidence);
        String feedback = stage(root, plan, client, PipelineStage.FEEDBACK, "No execution yet", evidence);
        assertTrue(feedback.contains("status: blocked"));
        String changed = client.execute(PipelineStage.TAD_TO_TEST_CASES, skills.load("tad-to-test-yaml"),
                tad + "\nBR-004: quantity=-1 must be rejected; add a fourth distinct case.", evidence, List.of());
        save(root, "quality-assets/evidence/changed-requirement.testcases.yaml", changed);
        boolean fixed = cases.equals(changed);
        assertTrue(fixed, "Golden mode is intentionally deterministic; update this check when model behavior changes");
        check("requirement-change-probe", false, "Golden mode ignored BR-004 and returned identical three cases; actual AI analysis not validated");
        if (execute) runRealExecution(root, plan, client);
        else report.put("execution", "not requested in ordinary unit suite; set AI_TEST_FLOW_VALIDATION_ROOT to run Maven/local HTTP acceptance");
        report.put("checks", checks);
        report.put("finishedAt", Instant.now().toString());
        report.put("overallStatus", execute ? "GENERATION_AND_HTTP_EXECUTION_VERIFIED_WITH_FEEDBACK_GAP" : "ASSET_GENERATION_VERIFIED");
        save(root, "quality-assets/evidence/validation-report.json", json(report));
        List<Map<String, Object>> inventory = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                if (!path.startsWith(root.resolve("target"))) inventory.add(Map.of("path", root.relativize(path).toString().replace('\\','/'), "sha256", ArtifactStore.sha256(Files.readAllBytes(path))));
            }
        }
        save(root, ".ai-test-flow/manifests/validation-inventory.json", json(Map.of("scope", "acceptance harness, not GUI TraceabilityService", "files", inventory)));
    }

    private String stage(Path root, AssetPlan plan, LlmClient client, PipelineStage stage, String input, String evidence) throws Exception {
        String context = skills.load(stage.skillName());
        assertFalse(context.isBlank());
        String result = client.execute(stage, context, input, evidence, List.of());
        var target = stage == PipelineStage.TEST_CASES_TO_API_SPEC ? new QualityGateService.InterfaceTarget(
                "HTTP", "HTTP_PATH", GoldenDemo.HTTP_SELECTOR, QualityGateService.extractCaseIds(input), List.of()) : null;
        var gate = gates.validate(stage, result, config, target);
        assertTrue(gate.passed(), stage + ": " + gate.errors());
        save(root, plan.pathFor(stage), ArtifactStore.clean(result));
        check(stage.name(), true, "Built-in skill loaded, default LlmClient route invoked, quality gate passed: " + plan.pathFor(stage));
        return result;
    }

    private void runRealExecution(Path root, AssetPlan plan, LlmClient client) throws Exception {
        AtomicBoolean faulty = new AtomicBoolean(false);
        List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/demo/orders", exchange -> {
            String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status; Map<String, String> body;
            try {
                JsonObject input = JsonParser.parseString(raw).getAsJsonObject();
                if (!exchange.getRequestMethod().equals("POST")) { status = 405; body = Map.of("code", "METHOD_NOT_ALLOWED"); }
                else if (!input.has("sku") || input.get("sku").isJsonNull() || input.get("sku").getAsString().isBlank()) { status = 400; body = Map.of("code", "SKU_REQUIRED"); }
                else if (!input.has("quantity") || input.get("quantity").getAsInt() <= 0) {
                    status = faulty.get() ? 200 : 400;
                    body = Map.of("code", "INVALID_QUANTITY");
                } else { status = 201; body = Map.of("code", "OK", "orderId", "ORDER-DEMO-001"); }
            } catch (RuntimeException error) { status = 400; body = Map.of("code", "INVALID_JSON"); }
            requests.add(Map.of("method", exchange.getRequestMethod(), "path", exchange.getRequestURI().getPath(), "input", raw, "responseStatus", status, "faulty", faulty.get()));
            byte[] response = json(body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, response.length);
            try (var stream = exchange.getResponseBody()) { stream.write(response); }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            int good = maven(root, baseUrl, "baseline");
            assertEquals(0, good, "Read quality-assets/evidence/baseline-maven.log");
            check("real-http-baseline", true, "Maven compiled main/test and ran initialization plus GOLD-001/002/003 against independent loopback HTTP fixture");
            faulty.set(true);
            int bad = maven(root, baseUrl, "fault-injection");
            assertNotEquals(0, bad, "Faulty status for zero quantity must be caught");
            assertTrue(Files.readString(root.resolve("quality-assets/evidence/fault-injection-results.jsonl")).contains("\"status\":\"FAILED\""));
            check("fault-detection", true, "Changed zero-quantity response status from 400 to 200; generated tests failed as required");
            faulty.set(false);
            assertEquals(0, maven(root, baseUrl, "recovered"));
            check("recovery-rerun", true, "Restored fixture; full suite passed again");
            String actual = Files.readString(root.resolve("quality-assets/evidence/recovered-results.jsonl"));
            for (String id : List.of("GOLD-001", "GOLD-002", "GOLD-003")) assertTrue(actual.contains(id));
            String after = client.execute(PipelineStage.FEEDBACK, skills.load("close-atc-feedback-loop"), actual,
                    "Real TestNG evidence from local synthetic HTTP fixture; no browser or company environment executed.\n" + actual, List.of());
            save(root, "quality-assets/evidence/feedback-after-real-execution.yaml", after);
            assertTrue(after.contains("status: blocked") && after.contains("not-run"));
            check("feedback-evidence-consumption", false, "Default golden feedback still says not-run after real TestNG evidence; automatic evidence-to-feedback closure is NOT verified");
            save(root, "quality-assets/evidence/http-requests.json", json(requests));
            report.put("execution", Map.of("fixture", "independent loopback synthetic service, not company backend", "baselineExit", good, "faultInjectionExit", bad,
                    "recoveredExit", 0, "httpRequestCount", requests.size(), "passedScope", "three HTTP business scenarios plus initialization smoke"));
        } finally { server.stop(0); }
    }

    private int maven(Path root, String baseUrl, String label) throws Exception {
        String reportPath = "quality-assets/evidence/" + label + "-results.jsonl";
        Path log = root.resolve("quality-assets/evidence/" + label + "-maven.log");
        Files.createDirectories(log.getParent());
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) command.addAll(List.of("cmd.exe", "/d", "/c", "mvn"));
        else command.add("mvn");
        command.addAll(List.of("-B", "-ntp", "test", "-Dapi.baseUrl=" + baseUrl, "-Dtestkit.report.file=" + reportPath));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Maven timed out: " + label); }
        save(root, "quality-assets/evidence/" + label + "-command.json", json(Map.of("command", command, "exitCode", process.exitValue())));
        Path xml = root.resolve("target/surefire-reports/TEST-TestSuite.xml");
        if (Files.exists(xml)) Files.copy(xml, root.resolve("quality-assets/evidence/" + label + "-testng.xml"));
        return process.exitValue();
    }

    private void check(String name, boolean passed, String detail) { checks.add(Map.of("check", name, "passed", passed, "detail", detail)); }
    private static String json(Object value) { return new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"; }
    private static void save(Path root, String path, String content) throws Exception {
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Acceptance output escaped project");
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    private static LlmClient client() {
        AiTestFlowSettings settings = new AiTestFlowSettings();
        settings.data().baseUrl = "deliberately-invalid-no-network-model";
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class}, (proxy, method, args) -> {
            if (method.getName().equals("getService") && args[0] == AiTestFlowSettings.class) return settings;
            throw new AssertionError("Acceptance harness did not authorize IDEA infrastructure access: " + method);
        });
        return new LlmClient(project);
    }
}
