package com.wanggw.api.scaffold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GenerationSpec {
    private int schemaVersion = 1;
    private String apiCode;
    private String description;
    private String packageName;
    private String resourcePackage;
    private String className;
    private String author = "AI";
    private String tester;
    private String dataKey;
    private String template = "query";
    private List<String> environments = new ArrayList<>(Arrays.asList("pre", "test"));
    private JavaTestSpec java = new JavaTestSpec();
    private List<CaseSpec> cases = new ArrayList<>();

    public void validate() {
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
        if (java == null) {
            throw new IllegalArgumentException("java section is required");
        }
        java.validate(template);
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("cases must contain at least one case");
        }
        for (int i = 0; i < cases.size(); i++) {
            cases.get(i).validate(i + 1);
        }
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
    public JavaTestSpec getJava() { return java; }
    public void setJava(JavaTestSpec java) { this.java = java; }
    public List<CaseSpec> getCases() { return cases; }
    public void setCases(List<CaseSpec> cases) { this.cases = cases; }

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
