package io.github.apitestkit.scaffold;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ApiTestScaffoldGenerator {
    public static final String GENERATOR_VERSION = "2.1.0";

    /** Computes every proposed output without writing to the project. */
    public Map<Path, String> preview(Path specFile, Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        return java.util.Collections.unmodifiableMap(buildOutputs(loadSpec(specFile), specFile, root));
    }

    public static String semanticFingerprint(Path specFile) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document = new Yaml(new SafeConstructor(options)).load(Files.readString(specFile, StandardCharsets.UTF_8));
        return sha256(new Yaml().dump(canonical(document)));
    }

    private static Object canonical(Object value) {
        if (value instanceof Map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
            return sorted;
        }
        if (value instanceof List) {
            List<Object> items = new ArrayList<>();
            for (Object item : (List<?>) value) items.add(canonical(item));
            return items;
        }
        return value;
    }

    public GenerationSpec loadSpec(Path specFile) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        Constructor constructor = new Constructor(GenerationSpec.class, options);
        Yaml yaml = new Yaml(constructor);
        try (Reader reader = Files.newBufferedReader(specFile, StandardCharsets.UTF_8)) {
            GenerationSpec spec = yaml.loadAs(reader, GenerationSpec.class);
            if (spec == null) {
                throw new IllegalArgumentException("spec is empty: " + specFile);
            }
            spec.validate();
            return spec;
        }
    }

    public GenerationResult generate(Path specFile, Path projectRoot, boolean overwrite) throws IOException {
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        GenerationSpec spec = loadSpec(specFile);
        Map<Path, String> outputs = buildOutputs(spec, specFile, normalizedProjectRoot);

        if (!overwrite) {
            List<Path> conflicts = new ArrayList<>();
            for (Path path : outputs.keySet()) {
                if (Files.exists(path)) {
                    conflicts.add(path);
                }
            }
            if (!conflicts.isEmpty()) {
                throw new IllegalStateException("refusing to overwrite existing files: " + conflicts);
            }
        }

        for (Map.Entry<Path, String> entry : outputs.entrySet()) {
            Files.createDirectories(entry.getKey().getParent());
            Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
        }

        Path manifest = metadataDirectory(normalizedProjectRoot, spec)
                .resolve("scaffold-manifest.yaml");
        Path plan = metadataDirectory(normalizedProjectRoot, spec)
                .resolve("executableization-plan.yaml");
        return new GenerationResult(spec.getApiCode(), normalizedProjectRoot, manifest, plan,
                new ArrayList<>(outputs.keySet()));
    }

    public ValidationReport validate(Path projectRoot, Path manifestFile, boolean strictChecksums)
            throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Map<String, Object> manifest = loadMap(manifestFile);
        List<String> missing = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> malformed = new ArrayList<>();

        Object filesValue = manifest.get("files");
        if (!(filesValue instanceof Collection)) {
            malformed.add("manifest.files must be a list");
        } else {
            for (Object item : (Collection<?>) filesValue) {
                if (!(item instanceof Map)) {
                    malformed.add("manifest.files contains a non-map item");
                    continue;
                }
                Map<?, ?> file = (Map<?, ?>) item;
                String relative = stringValue(file.get("path"));
                String expectedSha = stringValue(file.get("sha256"));
                if (relative == null || expectedSha == null) {
                    malformed.add("manifest file item requires path and sha256");
                    continue;
                }
                Path target = normalizedRoot.resolve(relative).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    malformed.add("manifest path escapes project root: " + relative);
                } else if (!Files.isRegularFile(target)) {
                    missing.add(relative);
                } else if (!expectedSha.equals(sha256(Files.readString(target, StandardCharsets.UTF_8)))) {
                    modified.add(relative);
                }
            }
        }

        String suiteFile = stringValue(manifest.get("suiteFile"));
        if (suiteFile == null) {
            malformed.add("manifest.suiteFile is required");
        } else {
            Path suite = normalizedRoot.resolve(suiteFile).normalize();
            if (Files.isRegularFile(suite)) {
                Map<String, Object> suiteData = loadMap(suite);
                if (!(suiteData.get("testDataSuite") instanceof Map)) {
                    malformed.add("suite root must contain testDataSuite");
                }
            }
        }

        boolean success = missing.isEmpty()
                && malformed.isEmpty()
                && (!strictChecksums || modified.isEmpty());
        return new ValidationReport(success, strictChecksums, missing, modified, malformed);
    }

    private Map<Path, String> buildOutputs(GenerationSpec spec, Path specFile, Path projectRoot) throws IOException {
        Map<Path, String> outputs = new LinkedHashMap<>();
        GenerationSpec.GeneratorSpec generator = spec.getGenerator();
        Map<?, ?> layout = Map.of();
        Path projectContract = projectRoot.resolve(".ai-test-flow/automation-project.yaml");
        if (!"generic".equals(spec.profile()) && Files.isRegularFile(projectContract)) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object parsed = new Yaml(new SafeConstructor(options)).load(Files.readString(projectContract));
            if (!(parsed instanceof Map)) throw new IOException("automation-project.yaml must be a YAML map");
            layout = (Map<?, ?>) parsed;
        }
        Path javaDirectory = resolveInside(
                outputRoot(projectRoot, layoutPath(layout, "testSourceRoot", generator.getSourceRoot()), "generator.sourceRoot"),
                packagePath(spec.getPackageName()), "packageName");
        Path javaFile = resolveInside(javaDirectory, spec.getClassName() + ".java", "className");
        Path resourceDirectory = resolveInside(
                outputRoot(projectRoot, layoutPath(layout, "testResourceRoot", generator.getResourceRoot()), "generator.resourceRoot"),
                packagePath(spec.getResourcePackage()), "resourcePackage");
        Path suiteFile = resolveInside(resourceDirectory, spec.getDataKey() + ".yaml", "dataKey");
        Path metadataDirectory = metadataDirectory(projectRoot, spec);
        Path planFile = metadataDirectory.resolve("executableization-plan.yaml");
        Path manifestFile = metadataDirectory.resolve("scaffold-manifest.yaml");

        outputs.put(javaFile, renderJava(spec));
        outputs.put(suiteFile, renderSuite(spec));
        if (!"generic".equals(spec.profile())) {
            if (!spec.getPackageName().equals(spec.getResourcePackage())) {
                throw new IllegalArgumentException("baseTest DataDriveUtil requires resourcePackage to match packageName");
            }
            Path mainDirectory = resolveInside(outputRoot(projectRoot, layoutPath(layout, "mainSourceRoot", generator.getMainSourceRoot()), "generator.mainSourceRoot"),
                    packagePath(spec.getPackageName()), "packageName");
            if (mainDirectory.equals(javaDirectory)) throw new IllegalArgumentException("mainSourceRoot and sourceRoot must be separate");
            outputs.put(mainDirectory.resolve(spec.getClassName() + "Api.java"), FrameworkScaffoldRenderer.api(spec));
            outputs.put(mainDirectory.resolve(spec.getClassName() + "DataBuilder.java"), FrameworkScaffoldRenderer.dataBuilder(spec));
            outputs.put(metadataDirectory.resolve("testng.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<suite name=\"Generated API tests\"><test name=\"Framework cases\"><classes><class name=\""
                    + spec.getPackageName() + "." + spec.getClassName() + "\"/></classes></test></suite>\n");
            outputs.put(metadataDirectory.resolve("FRAMEWORK-README.md"),
                    "# TestNG framework skeleton\n\n"
                    + "Test class extends your BaseTest and uses the project's installed runtime. DataProvider delegates to DataDriveUtil; "
                    + "CaseContext is bound by BaseTest. YAML selects JSON5 payloads. JsonDiff provides strict assertions.\n\n"
                    + "- " + spec.getClassName() + "Api: HTTP/Dubbo transport using framework clients.\n"
                    + "- " + spec.getClassName() + "DataBuilder: wire verified business data and cleanup.\n"
                    + "- Runtime: configure real identities/dataFactory at the composition root; no fake BO/VO or identity provider is invented.\n"
                    + "- Run with the existing project's TestNG runner and this interface's testng.xml.\n"
                    + "- main: API transport and business data hooks; test: TestNG methods and YAML/JSON5 resources.\n"
                    + "- Project initialization, build dependencies, bootstrap and environment configuration belong to the external project initializer.\n"
                    + "- Requires framework dependencies on the main compile classpath, not only test scope. No build file is generated or modified.\n"
                    + "- Framework suite/environment and identity configuration must be reviewed before live execution.\n");
        }

        for (GenerationSpec.CaseSpec caseSpec : spec.getCases()) {
            String requestBase = referenceBase(caseSpec.getRequestRef());
            String expectBase = referenceBase(caseSpec.getExpectRef());
            for (String environment : spec.getEnvironments()) {
                Map<String, Object> request = environmentPayload(caseSpec.getRequest(), environment);
                Map<String, Object> expect = environmentPayload(caseSpec.getExpect(), environment);
                outputs.put(resolveInside(resourceDirectory,
                                requestBase + "_req_" + environment + ".json5", "cases.requestRef"),
                        Json5Writer.write(caseSpec.getName() + " - " + environment + " request", request));
                outputs.put(resolveInside(resourceDirectory,
                                expectBase + "_exp_" + environment + ".json5", "cases.expectRef"),
                        Json5Writer.write(caseSpec.getName() + " - " + environment + " expected response", expect));
            }
        }

        String plan = renderExecutableizationPlan(spec, projectRoot, planFile);
        outputs.put(planFile, plan);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", spec.getSchemaVersion());
        manifest.put("generatorVersion", GENERATOR_VERSION);
        manifest.put("apiCode", spec.getApiCode());
        manifest.put("sourceCaseIds", spec.getSourceCaseIds());
        manifest.put("profile", spec.profile());
        if (spec.getTarget() != null) {
            manifest.put("protocol", spec.getTarget().normalizedProtocol());
            manifest.put("targetModule", spec.getTarget().getModule());
        }
        if (spec.getSelector() != null) {
            Map<String, Object> selector = new LinkedHashMap<>();
            selector.put("kind", spec.getSelector().getKind());
            selector.put("value", spec.getSelector().getValue());
            manifest.put("selector", selector);
        }
        manifest.put("types", renderTypesMetadata(spec.getTypes()));
        manifest.put("evidence", renderEvidenceMetadata(spec.getEvidence()));
        manifest.put("template", spec.getTemplate());
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("sourceSpec", specFile.toAbsolutePath().normalize().toString());
        manifest.put("sourceSpecSha256", sha256(Files.readString(specFile, StandardCharsets.UTF_8)));
        manifest.put("sourceSpecSemanticSha256", semanticFingerprint(specFile));
        manifest.put("testClass", relative(projectRoot, javaFile));
        manifest.put("suiteFile", relative(projectRoot, suiteFile));
        manifest.put("executableizationPlan", relative(projectRoot, planFile));
        List<Map<String, Object>> files = new ArrayList<>();
        for (Map.Entry<Path, String> output : outputs.entrySet()) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", relative(projectRoot, output.getKey()));
            file.put("sha256", sha256(output.getValue()));
            files.add(file);
        }
        manifest.put("files", files);
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("status", "pending-executableization");
        handoff.put("skill", "make-api-tests-executable");
        handoff.put("next", "process field hints, wire runtime data, compile and run the target test");
        manifest.put("handoff", handoff);
        outputs.put(manifestFile, dumpYaml(manifest));
        return outputs;
    }

    private String renderJava(GenerationSpec spec) {
        if (!"generic".equals(spec.profile())) return FrameworkScaffoldRenderer.test(spec);
        if ("basetest-http".equals(spec.profile())) {
            return renderBaseTestHttp(spec);
        }
        if ("basetest-dubbo".equals(spec.profile())) {
            return renderBaseTestDubbo(spec);
        }
        return renderGenericJava(spec);
    }

    private String renderGenericJava(GenerationSpec spec) {
        GenerationSpec.JavaTestSpec java = spec.getJava();
        StringBuilder out = new StringBuilder();
        out.append("package ").append(spec.getPackageName()).append(";\n\n");
        Set<String> imports = new LinkedHashSet<>(safeList(java.getImports()));
        imports.stream().filter(Objects::nonNull).sorted().forEach(value ->
                out.append("import ").append(value).append(";\n"));
        if (!imports.isEmpty()) {
            out.append('\n');
        }
        out.append("/**\n")
                .append(" * @author ").append(spec.getAuthor()).append('\n')
                .append(" * @create ").append(LocalDate.now()).append('\n')
                .append(" * @desc ").append(spec.getDescription()).append('\n')
                .append(" */\n");
        if (java.getClassAnnotations() != null) {
            for (String annotation : java.getClassAnnotations()) {
                out.append(annotation.replace("${tester}", spec.getTester() == null ? "" : spec.getTester()))
                        .append('\n');
            }
        }
        out.append("public class ").append(spec.getClassName())
                .append(" extends ").append(java.getBaseClass()).append(" {\n\n");
        appendIndentedLines(out, java.getClassMembers(), 4);
        if (java.getClassMembers() != null && !java.getClassMembers().isEmpty()) {
            out.append('\n');
        }
        out.append("    @DataProvider(name = \"").append(escapeJava(java.getDataProviderName()))
                .append("\")\n")
                .append("    public Object[][] ").append(java.getDataProviderMethod())
                .append("(Method method) {\n")
                .append("        return ")
                .append(java.getDataLoaderExpression().replace("${dataKey}", escapeJava(spec.getDataKey())))
                .append(";\n")
                .append("    }\n\n")
                .append("    @Test(dataProvider = \"").append(escapeJava(java.getDataProviderName()))
                .append("\", description = \"").append(escapeJava(spec.getDescription())).append("\")\n")
                .append("    public void ").append(java.getTestMethodName()).append("(\n");
        List<String> parameters = safeList(java.getTestParameters());
        for (int i = 0; i < parameters.size(); i++) {
            out.append("            ").append(parameters.get(i));
            if (i + 1 < parameters.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("    ) {\n");
        appendStepGroup(out, "准备请求上下文", java.getSetupLines());
        appendStepGroup(out, "准备运行时业务数据", java.getPreparationLines());
        out.append("        // 调用被测接口\n")
                .append("        ").append(java.getActualType()).append(" actual = ")
                .append(java.getActualExpression()).append(";\n\n")
                .append("        // 比对结果\n")
                .append("        List<String> diffFields = ").append(java.getDiffExpression()).append(";\n");
        if (java.getAssertionLines() == null || java.getAssertionLines().isEmpty()) {
            out.append("        Assert.assertTrue(CollectionUtils.isEmpty(diffFields),\n")
                    .append("                \"字段对比失败:\\n\" + String.join(\"\\n\", diffFields));\n");
        } else {
            appendIndentedLines(out, java.getAssertionLines(), 8);
        }
        out.append("    }\n}");
        out.append('\n');
        return out.toString();
    }

    private String renderBaseTestHttp(GenerationSpec spec) {
        GenerationSpec.EndpointSpec endpoint = spec.getEndpoint();
        GenerationSpec.GeneratorSpec generator = spec.getGenerator();
        StringBuilder out = new StringBuilder();
        out.append("package ").append(spec.getPackageName()).append(";\n\n")
                .append("import io.testkit.basetest.BaseTest;\n")
                .append("import io.testkit.basetest.config.ConfigLoader;\n")
                .append("import io.testkit.basetest.http.ApiClient;\n")
                .append("import io.testkit.basetest.http.ApiResponse;\n")
                .append("import java.util.Map;\n")
                .append("import org.testng.Assert;\n")
                .append("import org.testng.annotations.DataProvider;\n")
                .append("import org.testng.annotations.Test;\n\n")
                .append("/**\n")
                .append(" * Generated from API contract ").append(escapeJava(spec.getApiCode())).append(".\n")
                .append(" * Source cases: ").append(escapeJava(String.join(", ", spec.getSourceCaseIds())))
                .append(".\n")
                .append(" * @author ").append(spec.getAuthor()).append("\n")
                .append(" * @create ").append(LocalDate.now()).append("\n")
                .append(" */\n")
                .append("public class ").append(spec.getClassName()).append(" extends BaseTest {\n")
                .append("    private final ApiClient client = new ApiClient(\n")
                .append("            System.getProperty(\"")
                .append(escapeJava(generator.getBaseUrlProperty())).append("\", \"")
                .append(escapeJava(generator.getDefaultBaseUrl())).append("\"));\n\n");
        appendV2DataProvider(out, spec);
        out.append("    @Test(dataProvider = \"generatedCases\", description = \"")
                .append(escapeJava(spec.getDescription())).append("\")\n")
                .append("    public void generatedHttpApiTest(String sourceCaseId, String environment,\n")
                .append("                                     String requestResource, String expectedResource) {\n")
                .append("        Map<String, Object> request = readMap(requestResource);\n")
                .append("        Map<String, Object> expected = readMap(expectedResource);\n")
                .append("        ApiResponse actual = client.")
                .append(endpoint.isAuthenticated() ? "authenticated" : "request")
                .append("(\"").append(escapeJava(endpoint.getMethod().toUpperCase(Locale.ROOT)))
                .append("\", \"").append(escapeJava(endpoint.getPath())).append("\", ");
        if (!endpoint.isAuthenticated()) {
            out.append(renderStringMap(endpoint.getHeaders())).append(", ");
        }
        out.append("request);\n")
                .append("        assertHttpResponse(sourceCaseId, environment, expected, actual);\n")
                .append("    }\n\n")
                .append("    @SuppressWarnings(\"unchecked\")\n")
                .append("    private Map<String, Object> readMap(String resource) {\n")
                .append("        return ConfigLoader.readResource(resource, Map.class);\n")
                .append("    }\n\n")
                .append("    private void assertHttpResponse(String sourceCaseId, String environment,\n")
                .append("                                    Map<String, Object> expected, ApiResponse actual) {\n")
                .append("        Object expectedStatus = expected.get(\"status\");\n")
                .append("        if (expectedStatus != null) {\n")
                .append("            int status = expectedStatus instanceof Number\n")
                .append("                    ? ((Number) expectedStatus).intValue()\n")
                .append("                    : Integer.parseInt(String.valueOf(expectedStatus));\n")
                .append("            Assert.assertEquals(actual.status(), status,\n")
                .append("                    sourceCaseId + \" [\" + environment + \"] HTTP status\");\n")
                .append("        }\n")
                .append("        if (expected.containsKey(\"body\")) {\n")
                .append("            Assert.assertEquals(actual.json(),\n")
                .append("                    ConfigLoader.jsonMapper().valueToTree(expected.get(\"body\")),\n")
                .append("                    sourceCaseId + \" [\" + environment + \"] response body\");\n")
                .append("        } else if (!expected.containsKey(\"status\")) {\n")
                .append("            Assert.assertEquals(actual.json(),\n")
                .append("                    ConfigLoader.jsonMapper().valueToTree(expected),\n")
                .append("                    sourceCaseId + \" [\" + environment + \"] response body\");\n")
                .append("        }\n")
                .append("    }\n")
                .append("}\n");
        return out.toString();
    }

    private String renderBaseTestDubbo(GenerationSpec spec) {
        GenerationSpec.EndpointSpec endpoint = spec.getEndpoint();
        GenerationSpec.GeneratorSpec generator = spec.getGenerator();
        StringBuilder out = new StringBuilder();
        out.append("package ").append(spec.getPackageName()).append(";\n\n")
                .append("import io.testkit.adapters.dubbo.DubboClient;\n")
                .append("import io.testkit.basetest.BaseTest;\n")
                .append("import io.testkit.basetest.config.ConfigLoader;\n")
                .append("import java.time.Duration;\n")
                .append("import java.util.Map;\n")
                .append("import org.testng.Assert;\n")
                .append("import org.testng.annotations.AfterClass;\n")
                .append("import org.testng.annotations.BeforeClass;\n")
                .append("import org.testng.annotations.DataProvider;\n")
                .append("import org.testng.annotations.Test;\n\n")
                .append("/**\n")
                .append(" * Generated from Dubbo contract ").append(escapeJava(spec.getApiCode())).append(".\n")
                .append(" * Source cases: ").append(escapeJava(String.join(", ", spec.getSourceCaseIds())))
                .append(".\n")
                .append(" * @author ").append(spec.getAuthor()).append("\n")
                .append(" * @create ").append(LocalDate.now()).append("\n")
                .append(" */\n")
                .append("public class ").append(spec.getClassName()).append(" extends BaseTest {\n")
                .append("    private DubboClient client;\n\n")
                .append("    @BeforeClass(alwaysRun = true)\n")
                .append("    public void openDubboClient() {\n")
                .append("        client = new DubboClient(\"")
                .append(escapeJava(generator.getApplication())).append("\", \"")
                .append(escapeJava(endpoint.getInterfaceName())).append("\", \"")
                .append(escapeJava(nullToEmpty(generator.getGroup()))).append("\", \"")
                .append(escapeJava(nullToEmpty(generator.getVersion()))).append("\",\n")
                .append("                System.getProperty(\"")
                .append(escapeJava(generator.getRegistryProperty())).append("\"),\n")
                .append("                System.getProperty(\"")
                .append(escapeJava(generator.getDirectUrlProperty())).append("\"),\n")
                .append("                Duration.ofMillis(").append(generator.getTimeoutMillis()).append("L));\n")
                .append("    }\n\n")
                .append("    @AfterClass(alwaysRun = true)\n")
                .append("    public void closeDubboClient() {\n")
                .append("        if (client != null) {\n")
                .append("            client.close();\n")
                .append("        }\n")
                .append("    }\n\n");
        appendV2DataProvider(out, spec);
        out.append("    @Test(dataProvider = \"generatedCases\", description = \"")
                .append(escapeJava(spec.getDescription())).append("\")\n")
                .append("    public void generatedDubboApiTest(String sourceCaseId, String environment,\n")
                .append("                                      String requestResource, String expectedResource) {\n")
                .append("        Map<String, Object> request = readMap(requestResource);\n")
                .append("        Map<String, Object> expected = readMap(expectedResource);\n")
                .append("        Object actual = client.invoke(\"")
                .append(escapeJava(endpoint.getDubboMethod())).append("\",\n")
                .append("                ").append(renderStringArray(endpoint.getParameterTypes())).append(",\n")
                .append("                ").append(renderDubboArguments(endpoint)).append(",\n")
                .append("                ").append(renderStringMap(endpoint.getAttachments())).append(");\n")
                .append("        Assert.assertEquals(ConfigLoader.jsonMapper().valueToTree(actual),\n")
                .append("                ConfigLoader.jsonMapper().valueToTree(expected),\n")
                .append("                sourceCaseId + \" [\" + environment + \"] Dubbo response\");\n")
                .append("    }\n\n")
                .append("    @SuppressWarnings(\"unchecked\")\n")
                .append("    private Map<String, Object> readMap(String resource) {\n")
                .append("        return ConfigLoader.readResource(resource, Map.class);\n")
                .append("    }\n")
                .append("}\n");
        return out.toString();
    }

    private void appendV2DataProvider(StringBuilder out, GenerationSpec spec) {
        String prefix = packagePath(spec.getResourcePackage()) + "/";
        out.append("    @DataProvider(name = \"generatedCases\")\n")
                .append("    public Object[][] generatedCases() {\n")
                .append("        return new Object[][] {\n");
        for (int caseIndex = 0; caseIndex < spec.getCases().size(); caseIndex++) {
            GenerationSpec.CaseSpec item = spec.getCases().get(caseIndex);
            String sourceCaseId = sourceCaseId(spec, item, caseIndex);
            for (String environment : spec.getEnvironments()) {
                String request = prefix + referenceBase(item.getRequestRef())
                        + "_req_" + environment + ".json5";
                String expected = prefix + referenceBase(item.getExpectRef())
                        + "_exp_" + environment + ".json5";
                out.append("            {\"").append(escapeJava(sourceCaseId)).append("\", \"")
                        .append(escapeJava(environment)).append("\", \"")
                        .append(escapeJava(request)).append("\", \"")
                        .append(escapeJava(expected)).append("\"},\n");
            }
        }
        out.append("        };\n")
                .append("    }\n\n");
    }

    private String sourceCaseId(GenerationSpec spec, GenerationSpec.CaseSpec item, int index) {
        if (item.getSourceCaseId() != null && !item.getSourceCaseId().isBlank()) {
            return item.getSourceCaseId();
        }
        if (index < spec.getSourceCaseIds().size()) {
            return spec.getSourceCaseIds().get(index);
        }
        return item.getName();
    }

    private String renderDubboArguments(GenerationSpec.EndpointSpec endpoint) {
        if (endpoint.getParameterTypes().size() == 1) {
            return "new Object[] {request}";
        }
        StringBuilder out = new StringBuilder("new Object[] {");
        for (int i = 0; i < endpoint.getParameterNames().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append("request.get(\"").append(escapeJava(endpoint.getParameterNames().get(i)))
                    .append("\")");
        }
        return out.append('}').toString();
    }

    private String renderStringArray(List<String> values) {
        StringBuilder out = new StringBuilder("new String[] {");
        for (int i = 0; i < safeList(values).size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(escapeJava(values.get(i))).append('"');
        }
        return out.append('}').toString();
    }

    private String renderStringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "Map.of()";
        }
        StringBuilder out = new StringBuilder("Map.ofEntries(");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ > 0) {
                out.append(", ");
            }
            out.append("Map.entry(\"").append(escapeJava(entry.getKey())).append("\", \"")
                    .append(escapeJava(entry.getValue())).append("\")");
        }
        return out.append(')').toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String renderSuite(GenerationSpec spec) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("description", spec.getDescription());
        List<Map<String, Object>> cases = new ArrayList<>();
        for (GenerationSpec.CaseSpec caseSpec : spec.getCases()) {
          for (String environment : "generic".equals(spec.profile()) ? List.of("") : spec.getEnvironments()) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (caseSpec.getSourceCaseId() != null) {
                item.put("sourceCaseId", caseSpec.getSourceCaseId());
            }
            item.put("name", caseSpec.getName());
            if (!environment.isEmpty()) {
                item.put("name", caseSpec.getSourceCaseId() + " / " + environment + " / " + caseSpec.getName());
                item.put("extraParams", Map.of("sourceCaseId", caseSpec.getSourceCaseId(), "environment", environment));
            }
            item.put("thought", caseSpec.getThought());
            item.put("mockId", caseSpec.getMockId());
            if (caseSpec.getUsers() != null && !caseSpec.getUsers().isEmpty()) {
                item.put("users", caseSpec.getUsers());
            }
            item.put("request", List.of(environment.isEmpty() ? caseSpec.getRequestRef() : referenceBase(caseSpec.getRequestRef()) + "_req_" + environment + ".json5"));
            item.put("expect", List.of(environment.isEmpty() ? caseSpec.getExpectRef() : referenceBase(caseSpec.getExpectRef()) + "_exp_" + environment + ".json5"));
            cases.add(item);
          }
        }
        suite.put("cases", cases);
        root.put("testDataSuite", suite);
        return dumpYaml(root);
    }

    private String renderExecutableizationPlan(GenerationSpec spec, Path projectRoot, Path planFile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", spec.getSchemaVersion());
        root.put("apiCode", spec.getApiCode());
        root.put("sourceCaseIds", spec.getSourceCaseIds());
        root.put("profile", spec.profile());
        if (spec.getTarget() != null) {
            root.put("protocol", spec.getTarget().normalizedProtocol());
        }
        root.put("types", renderTypesMetadata(spec.getTypes()));
        root.put("evidence", renderEvidenceMetadata(spec.getEvidence()));
        root.put("status", "pending");
        root.put("projectRoot", projectRoot.toString());
        root.put("manifest", relative(projectRoot, planFile.getParent().resolve("scaffold-manifest.yaml")));
        List<Map<String, Object>> cases = new ArrayList<>();
        for (GenerationSpec.CaseSpec caseSpec : spec.getCases()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceCaseId", caseSpec.getSourceCaseId());
            item.put("name", caseSpec.getName());
            item.put("thought", caseSpec.getThought());
            item.put("requestRef", caseSpec.getRequestRef());
            item.put("expectRef", caseSpec.getExpectRef());
            List<Map<String, Object>> fields = new ArrayList<>();
            if (caseSpec.getFieldHints() != null) {
                for (GenerationSpec.FieldHint hint : caseSpec.getFieldHints()) {
                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("field", hint.getField());
                    field.put("kind", hint.getKind());
                    field.put("semantic", hint.getSemantic());
                    field.put("valueSource", hint.getValueSource());
                    field.put("placeholder", hint.getPlaceholder());
                    field.put("required", hint.isRequired());
                    field.put("decision", "pending");
                    fields.add(field);
                }
            }
            item.put("fields", fields);
            cases.add(item);
        }
        root.put("cases", cases);
        root.put("nextSkill", "make-api-tests-executable");
        root.put("rules", List.of(
                "preserve explicit negative values",
                "resolve enum values only from explicit mappings",
                "replace volatile IDs with runtime data references",
                "do not change business expectations to match failures"));
        return dumpYaml(root);
    }

    private static String layoutPath(Map<?, ?> layout, String key, String fallback) {
        Object value = layout.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String) || ((String) value).isBlank()) throw new IllegalArgumentException("Invalid project layout: " + key);
        return (String) value;
    }

    private List<Map<String, Object>> renderEvidenceMetadata(List<GenerationSpec.EvidenceSpec> evidence) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GenerationSpec.EvidenceSpec item : safeList(evidence)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", item.getOrder());
            row.put("kind", item.getKind());
            row.put("symbol", item.getSymbol());
            row.put("file", item.getFile());
            row.put("line", item.getLine());
            row.put("sha256", item.getSha256());
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> renderTypesMetadata(GenerationSpec.TypesSpec types) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (types == null) {
            return result;
        }
        result.put("request", renderTypeMetadata(types.getRequest()));
        result.put("response", renderTypeMetadata(types.getResponse()));
        List<Map<String, Object>> assets = new ArrayList<>();
        for (GenerationSpec.TypeRef item : safeList(types.getAssets())) {
            assets.add(renderTypeMetadata(item));
        }
        result.put("assets", assets);
        return result;
    }

    private Map<String, Object> renderTypeMetadata(GenerationSpec.TypeRef type) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (type == null) {
            return result;
        }
        result.put("kind", type.getKind());
        result.put("fqcn", type.getFqcn());
        result.put("sourceFile", type.getSourceFile());
        result.put("sha256", type.getSha256());
        result.put("fields", type.getFields());
        return result;
    }

    private Map<String, Object> environmentPayload(GenerationSpec.PayloadSpec payload, String environment) {
        Map<String, Object> merged = deepCopy(payload.getBase());
        if (payload.getOverrides() != null && payload.getOverrides().get(environment) != null) {
            deepMerge(merged, payload.getOverrides().get(environment));
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> override) {
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object current = target.get(entry.getKey());
            Object incoming = entry.getValue();
            if (current instanceof Map && incoming instanceof Map) {
                deepMerge((Map<String, Object>) current, (Map<String, Object>) incoming);
            } else {
                target.put(entry.getKey(), deepCopyValue(incoming));
            }
        }
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            return deepCopy((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private Map<String, Object> loadMap(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map)) {
                throw new IllegalArgumentException("YAML root must be a map: " + file);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) loaded;
            return result;
        }
    }

    private String dumpYaml(Map<String, Object> value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        options.setAllowUnicode(true);
        options.setSplitLines(false);
        return new Yaml(options).dump(value);
    }

    private static String packagePath(String packageName) {
        return packageName.replace('.', '/');
    }

    private static String referenceBase(String reference) {
        String name = reference;
        String lower = reference.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json5")) {
            name = reference.substring(0, reference.length() - 6);
        } else if (lower.endsWith(".json")) {
            name = reference.substring(0, reference.length() - 5);
        }
        return name;
    }

    private static Path metadataDirectory(Path projectRoot, GenerationSpec spec) {
        return resolveInside(outputRoot(projectRoot, spec.getGenerator().getMetadataRoot(),
                "generator.metadataRoot"), spec.getApiCode(), "apiCode");
    }

    private static Path outputRoot(Path projectRoot, String configured, String field) {
        Path candidate = projectRoot.resolve(configured).normalize();
        if (!candidate.startsWith(projectRoot)) {
            throw new IllegalArgumentException(field + " must stay inside project root");
        }
        return candidate;
    }

    private static Path resolveInside(Path root, String relative, String field) {
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException(field + " must stay inside its configured output root");
        }
        return candidate;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static void appendIndentedLines(StringBuilder out, List<String> lines, int spaces) {
        if (lines == null) {
            return;
        }
        String prefix = " ".repeat(spaces);
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                out.append('\n');
            } else {
                out.append(prefix).append(line).append('\n');
            }
        }
    }

    private static void appendStepGroup(StringBuilder out, String label, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        out.append("        // ").append(label).append('\n');
        appendIndentedLines(out, lines, 8);
        out.append('\n');
    }

    public static final class GenerationResult {
        private final String apiCode;
        private final Path projectRoot;
        private final Path manifest;
        private final Path executableizationPlan;
        private final List<Path> files;

        private GenerationResult(String apiCode, Path projectRoot, Path manifest,
                                 Path executableizationPlan, List<Path> files) {
            this.apiCode = apiCode;
            this.projectRoot = projectRoot;
            this.manifest = manifest;
            this.executableizationPlan = executableizationPlan;
            this.files = List.copyOf(files);
        }

        public String getApiCode() { return apiCode; }
        public Path getProjectRoot() { return projectRoot; }
        public Path getManifest() { return manifest; }
        public Path getExecutableizationPlan() { return executableizationPlan; }
        public List<Path> getFiles() { return files; }
    }

    public static final class ValidationReport {
        private final boolean success;
        private final boolean strictChecksums;
        private final List<String> missingFiles;
        private final List<String> modifiedFiles;
        private final List<String> malformedEntries;

        private ValidationReport(boolean success, boolean strictChecksums, List<String> missingFiles,
                                 List<String> modifiedFiles, List<String> malformedEntries) {
            this.success = success;
            this.strictChecksums = strictChecksums;
            this.missingFiles = List.copyOf(missingFiles);
            this.modifiedFiles = List.copyOf(modifiedFiles);
            this.malformedEntries = List.copyOf(malformedEntries);
        }

        public boolean isSuccess() { return success; }
        public boolean isStrictChecksums() { return strictChecksums; }
        public List<String> getMissingFiles() { return missingFiles; }
        public List<String> getModifiedFiles() { return modifiedFiles; }
        public List<String> getMalformedEntries() { return malformedEntries; }
    }
}
