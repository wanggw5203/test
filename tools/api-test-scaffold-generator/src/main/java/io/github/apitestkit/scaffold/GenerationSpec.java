package io.github.apitestkit.scaffold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GenerationSpec {
    private int schemaVersion = 1;
    private String apiCode;
    private List<String> sourceCaseIds = new ArrayList<>();
    private String description;
    private String packageName;
    private String resourcePackage;
    private String className;
    private String author = "AI";
    private String tester;
    private String dataKey;
    private String template = "query";
    private List<String> environments = new ArrayList<>(Arrays.asList("pre", "test"));
    private TargetSpec target;
    private SelectorSpec selector;
    private EndpointSpec endpoint;
    private TypesSpec types;
    private List<EvidenceSpec> evidence = new ArrayList<>();
    private GeneratorSpec generator = new GeneratorSpec();
    private JavaTestSpec java = new JavaTestSpec();
    private List<CaseSpec> cases = new ArrayList<>();

    public void validate() {
        if (schemaVersion < 1 || schemaVersion > 2) {
            throw new IllegalArgumentException("schemaVersion must be 1 or 2");
        }
        require(apiCode, "apiCode");
        require(description, "description");
        require(packageName, "packageName");
        require(className, "className");
        require(dataKey, "dataKey");
        if (resourcePackage == null || resourcePackage.trim().isEmpty()) {
            resourcePackage = packageName;
        }
        if (!"query".equals(template) && !"data-preparation".equals(template)) {
            throw new IllegalArgumentException("template must be query or data-preparation");
        }
        if (environments == null || environments.isEmpty()) {
            throw new IllegalArgumentException("environments must contain at least one environment");
        }
        for (int i = 0; i < environments.size(); i++) {
            require(environments.get(i), "environments[" + i + "]");
        }
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("cases must contain at least one case");
        }
        for (int i = 0; i < cases.size(); i++) {
            cases.get(i).validate(i + 1);
        }

        String profile = profile();
        if ("generic".equals(profile)) {
            if (java == null) {
                throw new IllegalArgumentException("java section is required for generic profile");
            }
            java.validate(template);
        } else if ("basetest-http".equals(profile)) {
            validateV2Envelope("HTTP");
            endpoint.validateHttp();
        } else if ("basetest-dubbo".equals(profile)) {
            validateV2Envelope("DUBBO");
            endpoint.validateDubbo();
        } else {
            throw new IllegalArgumentException(
                    "generator.profile must be generic, basetest-http or basetest-dubbo");
        }

        if (schemaVersion >= 2) {
            normalizeSourceCaseIds();
            if (selector != null) {
                selector.validate();
            }
        }
        if (generator == null) {
            generator = new GeneratorSpec();
        }
        generator.validate();
        if ("basetest-http".equals(profile)) {
            generator.validateHttp();
        } else if ("basetest-dubbo".equals(profile)) {
            generator.validateDubbo();
        }
    }

    private void validateV2Envelope(String expectedProtocol) {
        if (schemaVersion < 2) {
            throw new IllegalArgumentException("baseTest profiles require schemaVersion: 2");
        }
        if (target == null) {
            throw new IllegalArgumentException("target section is required for " + profile() + " profile");
        }
        target.validate();
        if (!expectedProtocol.equals(target.normalizedProtocol())) {
            throw new IllegalArgumentException(
                    "generator.profile " + profile() + " requires target.protocol " + expectedProtocol);
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint section is required for " + profile() + " profile");
        }
    }

    private void normalizeSourceCaseIds() {
        if (sourceCaseIds == null) {
            sourceCaseIds = new ArrayList<>();
        }
        if (sourceCaseIds.isEmpty()) {
            for (CaseSpec item : cases) {
                if (item.getSourceCaseId() == null || item.getSourceCaseId().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "schemaVersion 2 requires sourceCaseIds or cases[].sourceCaseId");
                }
                sourceCaseIds.add(item.getSourceCaseId());
            }
        }
        Set<String> unique = new LinkedHashSet<>();
        for (int i = 0; i < sourceCaseIds.size(); i++) {
            String value = sourceCaseIds.get(i);
            require(value, "sourceCaseIds[" + i + "]");
            if (!unique.add(value)) {
                throw new IllegalArgumentException("sourceCaseIds must be unique: " + value);
            }
        }
        for (int i = 0; i < cases.size(); i++) {
            CaseSpec item = cases.get(i);
            if (item.getSourceCaseId() == null || item.getSourceCaseId().trim().isEmpty()) {
                if (i >= sourceCaseIds.size()) {
                    throw new IllegalArgumentException(
                            "every v2 case must map to a sourceCaseId: cases[" + i + "]");
                }
                item.setSourceCaseId(sourceCaseIds.get(i));
            }
            if (!unique.contains(item.getSourceCaseId())) {
                throw new IllegalArgumentException(
                        "cases[" + i + "].sourceCaseId is not declared in sourceCaseIds: "
                                + item.getSourceCaseId());
            }
        }
    }

    public String profile() {
        if (generator == null || generator.getProfile() == null
                || generator.getProfile().trim().isEmpty()) {
            return "generic";
        }
        return generator.getProfile().trim().toLowerCase(Locale.ROOT);
    }

    private static void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getApiCode() { return apiCode; }
    public void setApiCode(String apiCode) { this.apiCode = apiCode; }
    public List<String> getSourceCaseIds() { return sourceCaseIds; }
    public void setSourceCaseIds(List<String> sourceCaseIds) { this.sourceCaseIds = sourceCaseIds; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getResourcePackage() { return resourcePackage; }
    public void setResourcePackage(String resourcePackage) { this.resourcePackage = resourcePackage; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getTester() { return tester; }
    public void setTester(String tester) { this.tester = tester; }
    public String getDataKey() { return dataKey; }
    public void setDataKey(String dataKey) { this.dataKey = dataKey; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public List<String> getEnvironments() { return environments; }
    public void setEnvironments(List<String> environments) { this.environments = environments; }
    public TargetSpec getTarget() { return target; }
    public void setTarget(TargetSpec target) { this.target = target; }
    public SelectorSpec getSelector() { return selector; }
    public void setSelector(SelectorSpec selector) { this.selector = selector; }
    public EndpointSpec getEndpoint() { return endpoint; }
    public void setEndpoint(EndpointSpec endpoint) { this.endpoint = endpoint; }
    public TypesSpec getTypes() { return types; }
    public void setTypes(TypesSpec types) { this.types = types; }
    public List<EvidenceSpec> getEvidence() { return evidence; }
    public void setEvidence(List<EvidenceSpec> evidence) { this.evidence = evidence; }
    public GeneratorSpec getGenerator() { return generator; }
    public void setGenerator(GeneratorSpec generator) { this.generator = generator; }
    public JavaTestSpec getJava() { return java; }
    public void setJava(JavaTestSpec java) { this.java = java; }
    public List<CaseSpec> getCases() { return cases; }
    public void setCases(List<CaseSpec> cases) { this.cases = cases; }

    public static class TargetSpec {
        private String protocol;
        private String module;

        private void validate() { require(protocol, "target.protocol"); }
        public String normalizedProtocol() { return protocol.trim().toUpperCase(Locale.ROOT); }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
    }

    public static class SelectorSpec {
        private String kind;
        private String value;

        private void validate() {
            require(kind, "selector.kind");
            require(value, "selector.value");
        }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class EndpointSpec {
        private String method;
        private String path;
        private boolean authenticated;
        private Map<String, String> headers = new LinkedHashMap<>();
        private String interfaceName;
        private String dubboMethod;
        private List<String> parameterTypes = new ArrayList<>();
        private List<String> parameterNames = new ArrayList<>();
        private Map<String, String> attachments = new LinkedHashMap<>();

        private void validateHttp() {
            require(method, "endpoint.method");
            require(path, "endpoint.path");
            if (!path.startsWith("/")) {
                throw new IllegalArgumentException("endpoint.path must start with /");
            }
        }

        private void validateDubbo() {
            require(interfaceName, "endpoint.interfaceName");
            require(dubboMethod, "endpoint.dubboMethod");
            if (parameterTypes == null || parameterTypes.isEmpty()) {
                throw new IllegalArgumentException("endpoint.parameterTypes must not be empty");
            }
            if (parameterTypes.size() > 1
                    && (parameterNames == null || parameterNames.size() != parameterTypes.size())) {
                throw new IllegalArgumentException(
                        "endpoint.parameterNames must match parameterTypes for multi-argument Dubbo methods");
            }
        }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isAuthenticated() { return authenticated; }
        public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
        public String getDubboMethod() { return dubboMethod; }
        public void setDubboMethod(String dubboMethod) { this.dubboMethod = dubboMethod; }
        public List<String> getParameterTypes() { return parameterTypes; }
        public void setParameterTypes(List<String> parameterTypes) { this.parameterTypes = parameterTypes; }
        public List<String> getParameterNames() { return parameterNames; }
        public void setParameterNames(List<String> parameterNames) { this.parameterNames = parameterNames; }
        public Map<String, String> getAttachments() { return attachments; }
        public void setAttachments(Map<String, String> attachments) { this.attachments = attachments; }
    }

    public static class TypesSpec {
        private TypeRef request;
        private TypeRef response;
        private List<TypeRef> assets = new ArrayList<>();

        public TypeRef getRequest() { return request; }
        public void setRequest(TypeRef request) { this.request = request; }
        public TypeRef getResponse() { return response; }
        public void setResponse(TypeRef response) { this.response = response; }
        public List<TypeRef> getAssets() { return assets; }
        public void setAssets(List<TypeRef> assets) { this.assets = assets; }
    }

    public static class TypeRef {
        private String kind;
        private String fqcn;
        private String sourceFile;
        private String sha256;
        private List<Map<String, Object>> fields = new ArrayList<>();

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getFqcn() { return fqcn; }
        public void setFqcn(String fqcn) { this.fqcn = fqcn; }
        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public List<Map<String, Object>> getFields() { return fields; }
        public void setFields(List<Map<String, Object>> fields) { this.fields = fields; }
    }

    public static class EvidenceSpec {
        private int order;
        private String kind;
        private String symbol;
        private String file;
        private Integer line;
        private String sha256;

        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public Integer getLine() { return line; }
        public void setLine(Integer line) { this.line = line; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
    }

    public static class GeneratorSpec {
        private void validateHttp() {
            require(baseUrlProperty, "generator.baseUrlProperty");
            require(defaultBaseUrl, "generator.defaultBaseUrl");
        }

        private void validateDubbo() {
            require(application, "generator.application");
            require(registryProperty, "generator.registryProperty");
            require(directUrlProperty, "generator.directUrlProperty");
        }
        private String profile = "generic";
        private String sourceRoot = "src/test/java";
        private String mainSourceRoot = "src/main/java";
        private String resourceRoot = "src/test/resources";
        private String metadataRoot = ".api-test-generator";
        private String baseUrlProperty = "api.baseUrl";
        private String defaultBaseUrl = "http://localhost:8080/";
        private String application = "ai-test-scaffold";
        private String registryProperty = "dubbo.registry";
        private String directUrlProperty = "dubbo.directUrl";
        private String group = "";
        private String version = "";
        private long timeoutMillis = 10000L;

        private void validate() {
            require(profile, "generator.profile");
            require(sourceRoot, "generator.sourceRoot");
            require(mainSourceRoot, "generator.mainSourceRoot");
            require(resourceRoot, "generator.resourceRoot");
            require(metadataRoot, "generator.metadataRoot");
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("generator.timeoutMillis must be positive");
            }
        }

        public String getProfile() { return profile; }
        public void setProfile(String profile) { this.profile = profile; }
        public String getSourceRoot() { return sourceRoot; }
        public String getMainSourceRoot() { return mainSourceRoot; }
        public void setMainSourceRoot(String mainSourceRoot) { this.mainSourceRoot = mainSourceRoot; }
        public void setSourceRoot(String sourceRoot) { this.sourceRoot = sourceRoot; }
        public String getResourceRoot() { return resourceRoot; }
        public void setResourceRoot(String resourceRoot) { this.resourceRoot = resourceRoot; }
        public String getMetadataRoot() { return metadataRoot; }
        public void setMetadataRoot(String metadataRoot) { this.metadataRoot = metadataRoot; }
        public String getBaseUrlProperty() { return baseUrlProperty; }
        public void setBaseUrlProperty(String baseUrlProperty) { this.baseUrlProperty = baseUrlProperty; }
        public String getDefaultBaseUrl() { return defaultBaseUrl; }
        public void setDefaultBaseUrl(String defaultBaseUrl) { this.defaultBaseUrl = defaultBaseUrl; }
        public String getApplication() { return application; }
        public void setApplication(String application) { this.application = application; }
        public String getRegistryProperty() { return registryProperty; }
        public void setRegistryProperty(String registryProperty) { this.registryProperty = registryProperty; }
        public String getDirectUrlProperty() { return directUrlProperty; }
        public void setDirectUrlProperty(String directUrlProperty) { this.directUrlProperty = directUrlProperty; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    }

    public static class JavaTestSpec {
        private String baseClass = "BaseTest";
        private List<String> imports = new ArrayList<>();
        private List<String> classAnnotations = new ArrayList<>();
        private List<String> classMembers = new ArrayList<>();
        private String dataProviderName = "TestCase";
        private String dataProviderMethod = "prepareTestData";
        private String dataLoaderExpression = "DataDriveUtil.loadTestData(method, \"${dataKey}\", true)";
        private String testMethodName = "test001";
        private List<String> testParameters = new ArrayList<>();
        private List<String> setupLines = new ArrayList<>();
        private List<String> preparationLines = new ArrayList<>();
        private String actualType;
        private String actualExpression;
        private String diffExpression;
        private List<String> assertionLines = new ArrayList<>();

        private void validate(String template) {
            require(baseClass, "java.baseClass");
            require(dataProviderName, "java.dataProviderName");
            require(dataProviderMethod, "java.dataProviderMethod");
            require(dataLoaderExpression, "java.dataLoaderExpression");
            require(testMethodName, "java.testMethodName");
            require(actualType, "java.actualType");
            require(actualExpression, "java.actualExpression");
            require(diffExpression, "java.diffExpression");
            if (testParameters == null || testParameters.isEmpty()) {
                throw new IllegalArgumentException("java.testParameters must not be empty");
            }
            if ("data-preparation".equals(template)
                    && (preparationLines == null || preparationLines.isEmpty())) {
                throw new IllegalArgumentException("data-preparation template requires java.preparationLines");
            }
        }

        public String getBaseClass() { return baseClass; }
        public void setBaseClass(String baseClass) { this.baseClass = baseClass; }
        public List<String> getImports() { return imports; }
        public void setImports(List<String> imports) { this.imports = imports; }
        public List<String> getClassAnnotations() { return classAnnotations; }
        public void setClassAnnotations(List<String> classAnnotations) { this.classAnnotations = classAnnotations; }
        public List<String> getClassMembers() { return classMembers; }
        public void setClassMembers(List<String> classMembers) { this.classMembers = classMembers; }
        public String getDataProviderName() { return dataProviderName; }
        public void setDataProviderName(String dataProviderName) { this.dataProviderName = dataProviderName; }
        public String getDataProviderMethod() { return dataProviderMethod; }
        public void setDataProviderMethod(String dataProviderMethod) { this.dataProviderMethod = dataProviderMethod; }
        public String getDataLoaderExpression() { return dataLoaderExpression; }
        public void setDataLoaderExpression(String dataLoaderExpression) { this.dataLoaderExpression = dataLoaderExpression; }
        public String getTestMethodName() { return testMethodName; }
        public void setTestMethodName(String testMethodName) { this.testMethodName = testMethodName; }
        public List<String> getTestParameters() { return testParameters; }
        public void setTestParameters(List<String> testParameters) { this.testParameters = testParameters; }
        public List<String> getSetupLines() { return setupLines; }
        public void setSetupLines(List<String> setupLines) { this.setupLines = setupLines; }
        public List<String> getPreparationLines() { return preparationLines; }
        public void setPreparationLines(List<String> preparationLines) { this.preparationLines = preparationLines; }
        public String getActualType() { return actualType; }
        public void setActualType(String actualType) { this.actualType = actualType; }
        public String getActualExpression() { return actualExpression; }
        public void setActualExpression(String actualExpression) { this.actualExpression = actualExpression; }
        public String getDiffExpression() { return diffExpression; }
        public void setDiffExpression(String diffExpression) { this.diffExpression = diffExpression; }
        public List<String> getAssertionLines() { return assertionLines; }
        public void setAssertionLines(List<String> assertionLines) { this.assertionLines = assertionLines; }
    }

    public static class CaseSpec {
        private String sourceCaseId;
        private String name;
        private String thought;
        private Object mockId;
        private List<String> users = new ArrayList<>();
        private String requestRef;
        private String expectRef;
        private PayloadSpec request = new PayloadSpec();
        private PayloadSpec expect = new PayloadSpec();
        private List<FieldHint> fieldHints = new ArrayList<>();

        private void validate(int index) {
            require(name, "cases[" + index + "].name");
            require(thought, "cases[" + index + "].thought");
            require(requestRef, "cases[" + index + "].requestRef");
            require(expectRef, "cases[" + index + "].expectRef");
            if (request == null || expect == null) {
                throw new IllegalArgumentException("cases[" + index + "] request and expect are required");
            }
        }

        public String getSourceCaseId() { return sourceCaseId; }
        public void setSourceCaseId(String sourceCaseId) { this.sourceCaseId = sourceCaseId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getThought() { return thought; }
        public void setThought(String thought) { this.thought = thought; }
        public Object getMockId() { return mockId; }
        public void setMockId(Object mockId) { this.mockId = mockId; }
        public List<String> getUsers() { return users; }
        public void setUsers(List<String> users) { this.users = users; }
        public String getRequestRef() { return requestRef; }
        public void setRequestRef(String requestRef) { this.requestRef = requestRef; }
        public String getExpectRef() { return expectRef; }
        public void setExpectRef(String expectRef) { this.expectRef = expectRef; }
        public PayloadSpec getRequest() { return request; }
        public void setRequest(PayloadSpec request) { this.request = request; }
        public PayloadSpec getExpect() { return expect; }
        public void setExpect(PayloadSpec expect) { this.expect = expect; }
        public List<FieldHint> getFieldHints() { return fieldHints; }
        public void setFieldHints(List<FieldHint> fieldHints) { this.fieldHints = fieldHints; }
    }

    public static class PayloadSpec {
        private Map<String, Object> base = new LinkedHashMap<>();
        private Map<String, Map<String, Object>> overrides = new LinkedHashMap<>();

        public Map<String, Object> getBase() { return base; }
        public void setBase(Map<String, Object> base) { this.base = base; }
        public Map<String, Map<String, Object>> getOverrides() { return overrides; }
        public void setOverrides(Map<String, Map<String, Object>> overrides) { this.overrides = overrides; }
    }

    public static class FieldHint {
        private String field;
        private String kind;
        private String semantic;
        private String valueSource;
        private String placeholder;
        private boolean required;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getSemantic() { return semantic; }
        public void setSemantic(String semantic) { this.semantic = semantic; }
        public String getValueSource() { return valueSource; }
        public void setValueSource(String valueSource) { this.valueSource = valueSource; }
        public String getPlaceholder() { return placeholder; }
        public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }
}
