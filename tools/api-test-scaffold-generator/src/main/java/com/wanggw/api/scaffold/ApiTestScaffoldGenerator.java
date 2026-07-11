package com.wanggw.api.scaffold;

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
    public static final String GENERATOR_VERSION = "1.0.0";

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

    private Map<Path, String> buildOutputs(GenerationSpec spec, Path specFile, Path projectRoot) {
        Map<Path, String> outputs = new LinkedHashMap<>();
        Path javaFile = projectRoot.resolve("src/test/java")
                .resolve(packagePath(spec.getPackageName()))
                .resolve(spec.getClassName() + ".java");
        Path resourceDirectory = projectRoot.resolve("src/test/resources")
                .resolve(packagePath(spec.getResourcePackage()));
        Path suiteFile = resourceDirectory.resolve(spec.getDataKey() + ".yaml");
        Path metadataDirectory = metadataDirectory(projectRoot, spec);
        Path planFile = metadataDirectory.resolve("executableization-plan.yaml");
        Path manifestFile = metadataDirectory.resolve("scaffold-manifest.yaml");

        outputs.put(javaFile, renderJava(spec));
        outputs.put(suiteFile, renderSuite(spec));

        for (GenerationSpec.CaseSpec caseSpec : spec.getCases()) {
            String requestBase = referenceBase(caseSpec.getRequestRef());
            String expectBase = referenceBase(caseSpec.getExpectRef());
            for (String environment : spec.getEnvironments()) {
                Map<String, Object> request = environmentPayload(caseSpec.getRequest(), environment);
                Map<String, Object> expect = environmentPayload(caseSpec.getExpect(), environment);
                outputs.put(resourceDirectory.resolve(requestBase + "_req_" + environment + ".json5"),
                        Json5Writer.write(caseSpec.getName() + " - " + environment + " request", request));
                outputs.put(resourceDirectory.resolve(expectBase + "_exp_" + environment + ".json5"),
                        Json5Writer.write(caseSpec.getName() + " - " + environment + " expected response", expect));
            }
        }

        String plan = renderExecutableizationPlan(spec, projectRoot, planFile);
        outputs.put(planFile, plan);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("generatorVersion", GENERATOR_VERSION);
        manifest.put("apiCode", spec.getApiCode());
        manifest.put("template", spec.getTemplate());
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("sourceSpec", specFile.toAbsolutePath().normalize().toString());
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
        if (java.getClassAnnotations() == null || java.getClassAnnotations().isEmpty()) {
            if (spec.getTester() != null && !spec.getTester().isBlank()) {
                out.append("@PuTester(\"").append(escapeJava(spec.getTester())).append("\")\n");
            }
        } else {
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

    private String renderSuite(GenerationSpec spec) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("description", spec.getDescription());
        List<Map<String, Object>> cases = new ArrayList<>();
        for (GenerationSpec.CaseSpec caseSpec : spec.getCases()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", caseSpec.getName());
            item.put("thought", caseSpec.getThought());
            item.put("mockId", caseSpec.getMockId());
            if (caseSpec.getUsers() != null && !caseSpec.getUsers().isEmpty()) {
                item.put("users", caseSpec.getUsers());
            }
            item.put("request", List.of(caseSpec.getRequestRef()));
            item.put("expect", List.of(caseSpec.getExpectRef()));
            cases.add(item);
        }
        suite.put("cases", cases);
        root.put("testDataSuite", suite);
        return dumpYaml(root);
    }

    private String renderExecutableizationPlan(GenerationSpec spec, Path projectRoot, Path planFile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("apiCode", spec.getApiCode());
        root.put("status", "pending");
        root.put("projectRoot", projectRoot.toString());
        root.put("manifest", relative(projectRoot, planFile.getParent().resolve("scaffold-manifest.yaml")));
        List<Map<String, Object>> cases = new ArrayList<>();
        for (GenerationSpec.CaseSpec caseSpec : spec.getCases()) {
            Map<String, Object> item = new LinkedHashMap<>();
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
        return projectRoot.resolve(".api-test-generator").resolve(spec.getApiCode());
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
