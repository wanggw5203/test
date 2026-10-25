package io.github.apitestkit.scaffold;

import java.util.Map;
import java.util.stream.Collectors;

/** Templates aligned with baseTest/java-api-lab and the framework's real YAML loader. */
final class FrameworkScaffoldRenderer {
    private FrameworkScaffoldRenderer() { }

    static String test(GenerationSpec spec) {
        String type = spec.getClassName();
        boolean http = "basetest-http".equals(spec.profile());
        return String.join("\n",
                "package " + spec.getPackageName() + ";", "",
                "import io.testkit.basetest.BaseTest;",
                "import io.testkit.basetest.data.CaseContext;",
                "import io.testkit.basetest.data.DataDriveUtil;",
                "import io.testkit.basetest.assertion.JsonDiff;",
                "import io.testkit.basetest.enterprise.TestCaseMetadata;",
                "import io.testkit.basetest.runtime.PortableTestNgListener;",
                "import java.lang.reflect.Method;", "import java.util.Map;",
                "import org.testng.Assert;", "import org.testng.annotations.*;", "",
                "/** Framework skeleton for " + spec.getApiCode() + "; source cases: " + String.join(", ", spec.getSourceCaseIds()) + " */",
                "@Listeners(PortableTestNgListener.class)",
                "@TestCaseMetadata(id = \"" + j(spec.getApiCode()) + "\")",
                "public class " + type + " extends BaseTest {",
                "    // Runtime, identity, environment and reporting are installed by the existing project's bootstrap.",
                "    private final " + type + "DataBuilder dataBuilder = new " + type + "DataBuilder();",
                "    private " + type + "Api api;", "",
                "    @BeforeClass(alwaysRun = true)",
                "    public void setUp() { api = new " + type + "Api(); }", "",
                "    @AfterClass(alwaysRun = true)",
                "    public void tearDown() { if (api != null) api.close(); }", "",
                "    @DataProvider(name = \"frameworkCases\")",
                "    public Object[][] cases(Method method) {",
                "        return DataDriveUtil.loadTestData(method, System.getProperty(\"testkit.caseSuite\", \"" + j(spec.getDataKey()) + "\"));",
                "    }", "",
                "    @Test(dataProvider = \"frameworkCases\")",
                "    public void verifyApi(CaseContext context, Map<String, Object> request, Map<String, Object> expected) {",
                "        Assert.assertEquals(currentCase().name(), context.name());",
                "        Map<String, Object> prepared = dataBuilder.prepare(context, request);",
                "        deferCleanup(() -> dataBuilder.cleanup(context, prepared));",
                http ? "        io.testkit.basetest.http.ApiResponse response = api.invoke(prepared);" : "        Object actual = api.invoke(prepared);",
                http ? "        if (expected.containsKey(\"status\")) Assert.assertEquals(response.status(), ((Number) expected.get(\"status\")).intValue(), context.name());" : "",
                http ? "        Object actual = response.json();" : "",
                http ? "        Object expectedBody = expected.containsKey(\"body\") ? expected.get(\"body\") : expected;" : "        Object expectedBody = expected;",
                "        var differences = JsonDiff.strict(expectedBody, actual);",
                "        Assert.assertTrue(differences.isEmpty(), context.name() + \" : \" + differences);",
                "    }", "}", "");
    }

    static String api(GenerationSpec spec) {
        String type = spec.getClassName();
        GenerationSpec.EndpointSpec endpoint = spec.getEndpoint();
        GenerationSpec.GeneratorSpec generator = spec.getGenerator();
        String prefix = "package " + spec.getPackageName() + ";\n\nimport java.util.Map;\n";
        if ("basetest-http".equals(spec.profile())) {
            return prefix + String.join("\n", "import io.testkit.basetest.http.ApiClient;",
                    "import io.testkit.basetest.http.ApiResponse;", "",
                    "public class " + type + "Api implements AutoCloseable {",
                    "    private final ApiClient client = new ApiClient(System.getProperty(\"" + j(generator.getBaseUrlProperty()) + "\", \"" + j(generator.getDefaultBaseUrl()) + "\"));",
                    "    public ApiResponse invoke(Map<String, Object> request) {",
                    endpoint.isAuthenticated()
                            ? "        return client.authenticated(\"" + j(endpoint.getMethod()) + "\", \"" + j(endpoint.getPath()) + "\", request);"
                            : "        return client.request(\"" + j(endpoint.getMethod()) + "\", \"" + j(endpoint.getPath()) + "\", " + map(endpoint.getHeaders()) + ", request);",
                    "    }", "    @Override public void close() { }", "}", "");
        }
        String types = endpoint.getParameterTypes().stream().map(value -> "\"" + j(value) + "\"").collect(Collectors.joining(", "));
        String args = endpoint.getParameterTypes().size() == 1 ? "request" : endpoint.getParameterNames().stream()
                .map(value -> "request.get(\"" + j(value) + "\")").collect(Collectors.joining(", "));
        return prefix + String.join("\n", "import io.testkit.adapters.dubbo.DubboClient;", "import java.time.Duration;", "",
                "public class " + type + "Api implements AutoCloseable {",
                "    private final DubboClient client = new DubboClient(\"" + j(generator.getApplication()) + "\", \"" + j(endpoint.getInterfaceName()) + "\", \"" + j(generator.getGroup()) + "\", \"" + j(generator.getVersion()) + "\",",
                "            System.getProperty(\"" + j(generator.getRegistryProperty()) + "\"), System.getProperty(\"" + j(generator.getDirectUrlProperty()) + "\"), Duration.ofMillis(" + generator.getTimeoutMillis() + "L));",
                "    public Object invoke(Map<String, Object> request) {",
                "        return client.invoke(\"" + j(endpoint.getDubboMethod()) + "\", new String[] {" + types + "}, new Object[] {" + args + "}, " + map(endpoint.getAttachments()) + ");",
                "    }", "    @Override public void close() { client.close(); }", "}", "");
    }

    static String dataBuilder(GenerationSpec spec) {
        return String.join("\n", "package " + spec.getPackageName() + ";", "",
                "import io.testkit.basetest.data.CaseContext;", "import java.util.Map;", "import java.util.LinkedHashMap;", "",
                "/** Business data extension point. Wire the project's DataFactory/DataBuilder here. */",
                "public class " + spec.getClassName() + "DataBuilder {",
                "    public Map<String, Object> prepare(CaseContext context, Map<String, Object> request) {",
                "        // TODO: resolve runtime data references from verified business builders.",
                "        // Preserve explicit negative inputs; never replace them with valid data.",
                "        return new LinkedHashMap<>(request);", "    }",
                "    public void cleanup(CaseContext context, Map<String, Object> request) {",
                "        // TODO: release only the data created by this invocation.",
                "    }", "}", "");
    }

    private static String map(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "Map.of()";
        return "Map.ofEntries(" + map.entrySet().stream().map(entry -> "Map.entry(\"" + j(entry.getKey()) + "\", \"" + j(entry.getValue()) + "\")")
                .collect(Collectors.joining(", ")) + ")";
    }
    private static String j(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
}
