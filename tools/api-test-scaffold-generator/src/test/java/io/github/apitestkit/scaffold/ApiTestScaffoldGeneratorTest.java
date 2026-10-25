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

    @Test void projectContractControlsLayoutWithoutGeneratingBuildOrBootstrap() throws Exception {
        Path project = temporaryDirectory.resolve("custom-layout");
        Files.createDirectories(project.resolve(".ai-test-flow"));
        Files.writeString(project.resolve(".ai-test-flow/automation-project.yaml"),
                "schemaVersion: 1\nframework: io.testkit:base-test-toolkit\nframeworkScope: compile\nmainSourceRoot: module/src/main/java\ntestSourceRoot: module/src/test/java\ntestResourceRoot: module/src/test/resources\n");
        Path spec = temporaryDirectory.resolve("http.yaml");
        try (InputStream input = getClass().getResourceAsStream("/example-http-v2-spec.yaml")) { Files.copy(input, spec); }
        var generator = new ApiTestScaffoldGenerator();
        var result = generator.generate(spec, project, false);
        assertTrue(Files.exists(project.resolve("module/src/main/java/com/example/automation/order/OrderCreateHttpGeneratedTestsApi.java")));
        assertTrue(Files.exists(project.resolve("module/src/test/java/com/example/automation/order/OrderCreateHttpGeneratedTests.java")));
        assertFalse(result.getFiles().stream().anyMatch(path -> path.toString().endsWith("pom.xml")));
        assertTrue(generator.validate(project, result.getManifest(), true).isSuccess());
        Files.writeString(project.resolve(".ai-test-flow/automation-project.yaml"), "mainSourceRoot: ../outside\n");
        assertThrows(IllegalArgumentException.class, () -> generator.preview(spec, project));
    }

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

    @Test
    void generatesBaseTestHttpScaffoldFromV2Contract() throws Exception {
        Path spec = writeSpec("http-v2.yaml", String.join("\n",
                "schemaVersion: 2",
                "apiCode: order_create_http",
                "sourceCaseIds: [TC-HTTP-001]",
                "description: create order over HTTP",
                "packageName: com.example.generated.http",
                "className: OrderHttpGeneratedTests",
                "dataKey: order-http-cases",
                "environments: [test]",
                "target:",
                "  protocol: HTTP",
                "selector:",
                "  kind: HTTP_PATH",
                "  value: POST /api/orders",
                "endpoint:",
                "  method: POST",
                "  path: /api/orders",
                "  headers:",
                "    X-Client: ai-test",
                "types:",
                "  request:",
                "    kind: BO",
                "    fqcn: com.example.order.CreateOrderBO",
                "evidence:",
                "  - order: 1",
                "    kind: CONTROLLER",
                "    symbol: com.example.OrderController#create",
                "    file: src/main/java/com/example/OrderController.java",
                "generator:",
                "  profile: basetest-http",
                "cases:",
                "  - sourceCaseId: TC-HTTP-001",
                "    name: valid order",
                "    thought: verify successful creation",
                "    requestRef: valid-order.json",
                "    expectRef: valid-order.json",
                "    request:",
                "      base:",
                "        sku: SKU-1",
                "        quantity: 1",
                "    expect:",
                "      base:",
                "        status: 200",
                "        body:",
                "          success: true",
                ""));
        Path project = temporaryDirectory.resolve("http-v2-project");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();

        ApiTestScaffoldGenerator.GenerationResult result = generator.generate(spec, project, false);

        Path javaFile = project.resolve(
                "src/test/java/com/example/generated/http/OrderHttpGeneratedTests.java");
        String java = Files.readString(javaFile, StandardCharsets.UTF_8);
        assertFalse(java.contains("new AutomationRuntime"), "Generated test must not replace the existing project runtime");
        java += Files.readString(project.resolve("src/main/java/com/example/generated/http/OrderHttpGeneratedTestsApi.java"));
        assertFalse(result.getFiles().stream().anyMatch(path -> path.getFileName().toString().equals("pom.xml")));
        assertTrue(java.contains("import io.testkit.basetest.http.ApiClient;"));
        assertTrue(java.contains("client.request(\"POST\", \"/api/orders\""));
        assertTrue(java.contains("Map.entry(\"X-Client\", \"ai-test\")"));
        assertTrue(java.contains("TC-HTTP-001"));
        assertTrue(java.contains("DataDriveUtil.loadTestData"));
        assertTrue(java.contains("CaseContext context"));

        String manifest = Files.readString(result.getManifest(), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("profile: basetest-http"));
        assertTrue(manifest.contains("protocol: HTTP"));
        assertTrue(manifest.contains("CONTROLLER"));
        assertTrue(generator.validate(project, result.getManifest(), true).isSuccess());
    }

    @Test
    void generatesBaseTestDubboScaffoldFromV2Contract() throws Exception {
        Path spec = writeSpec("dubbo-v2.yaml", String.join("\n",
                "schemaVersion: 2",
                "apiCode: order_create_dubbo",
                "sourceCaseIds: [TC-DUBBO-001]",
                "description: create order over Dubbo",
                "packageName: com.example.generated.dubbo",
                "className: OrderDubboGeneratedTests",
                "dataKey: order-dubbo-cases",
                "environments: [test]",
                "target:",
                "  protocol: DUBBO",
                "selector:",
                "  kind: JAVA_SYMBOL",
                "  value: com.example.OrderService#create",
                "endpoint:",
                "  interfaceName: com.example.OrderService",
                "  dubboMethod: create",
                "  parameterTypes: [com.example.CreateOrderBO]",
                "  attachments:",
                "    x-source: ai-test",
                "generator:",
                "  profile: basetest-dubbo",
                "  application: generated-api-tests",
                "  timeoutMillis: 5000",
                "cases:",
                "  - sourceCaseId: TC-DUBBO-001",
                "    name: valid order",
                "    thought: verify generic Dubbo invocation",
                "    requestRef: valid-order.json",
                "    expectRef: valid-order.json",
                "    request:",
                "      base:",
                "        sku: SKU-1",
                "    expect:",
                "      base:",
                "        success: true",
                ""));
        Path project = temporaryDirectory.resolve("dubbo-v2-project");
        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();

        ApiTestScaffoldGenerator.GenerationResult result = generator.generate(spec, project, false);

        Path javaFile = project.resolve(
                "src/test/java/com/example/generated/dubbo/OrderDubboGeneratedTests.java");
        String java = Files.readString(javaFile, StandardCharsets.UTF_8);
        java += Files.readString(project.resolve("src/main/java/com/example/generated/dubbo/OrderDubboGeneratedTestsApi.java"));
        assertTrue(java.contains("import io.testkit.adapters.dubbo.DubboClient;"));
        assertTrue(java.contains("new DubboClient(\"generated-api-tests\""));
        assertTrue(java.contains("client.invoke(\"create\""));
        assertTrue(java.contains("new String[] {\"com.example.CreateOrderBO\"}"));
        assertTrue(java.contains("new Object[] {request}"));
        assertTrue(java.contains("Map.entry(\"x-source\", \"ai-test\")"));

        String manifest = Files.readString(result.getManifest(), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("profile: basetest-dubbo"));
        assertTrue(manifest.contains("protocol: DUBBO"));
        assertTrue(generator.validate(project, result.getManifest(), true).isSuccess());
    }

    @Test
    void rejectsMismatchedBaseTestProfileAndProtocol() throws Exception {
        Path spec = writeSpec("invalid-v2.yaml", String.join("\n",
                "schemaVersion: 2",
                "apiCode: invalid_contract",
                "sourceCaseIds: [TC-1]",
                "description: invalid contract",
                "packageName: com.example.generated",
                "className: InvalidTests",
                "dataKey: invalid-cases",
                "environments: [test]",
                "target:",
                "  protocol: DUBBO",
                "endpoint:",
                "  method: POST",
                "  path: /api/orders",
                "generator:",
                "  profile: basetest-http",
                "cases:",
                "  - name: invalid",
                "    thought: invalid protocol",
                "    requestRef: invalid.json",
                "    expectRef: invalid.json",
                "    request: {base: {}}",
                "    expect: {base: {}}",
                ""));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ApiTestScaffoldGenerator().loadSpec(spec));
        assertTrue(error.getMessage().contains("requires target.protocol HTTP"));
    }

    private Path writeSpec(String name, String content) throws Exception {
        Path spec = temporaryDirectory.resolve(name);
        Files.writeString(spec, content, StandardCharsets.UTF_8);
        return spec;
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
