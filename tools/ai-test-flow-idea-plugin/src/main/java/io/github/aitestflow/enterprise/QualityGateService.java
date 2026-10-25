package io.github.aitestflow.enterprise;

import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.discovery.DiscoveredEndpoint;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QualityGateService {
    private static final Pattern CASE_ID = Pattern.compile("(?m)^\\s*-?\\s*caseId:\\s*[\"']?([^\\s\"']+)");

    public GateResult validate(PipelineStage stage, String content, WorkspaceConfig config) {
        return validate(stage, content, config, null);
    }

    public GateResult validate(PipelineStage stage, String content, WorkspaceConfig config,
                               InterfaceTarget expectedInterface) {
        List<String> errors = new ArrayList<>();
        if (config.gates.rejectEmptyOutput && (content == null || content.isBlank())) errors.add("生成结果为空");
        String value = content == null ? "" : content;
        switch (stage) {
            case REQUIREMENT_TO_TAD -> {
                if (config.gates.requireTadSections) {
                    require(value, errors, "# 业务背景", "缺少业务背景章节");
                    require(value, errors, "# TRD", "缺少 TRD 章节");
                    require(value, errors, "# STD", "缺少 STD 章节");
                }
            }
            case TAD_TO_TEST_CASES -> validateCases(value, config, errors);
            case TEST_CASES_TO_API_SPEC -> {
                validateApiSpec(value, config, expectedInterface, errors);
            }
            case TEST_CASES_TO_WEB_ATC -> {
                require(value, errors, "suite:", "缺少 suite");
                require(value, errors, "TestCases:", "缺少 TestCases");
            }
            case FEEDBACK -> require(value, errors, "feedback:", "缺少 feedback 根节点");
        }
        return new GateResult(errors.isEmpty(), List.copyOf(errors));
    }

    private static void validateCases(String value, WorkspaceConfig config, List<String> errors) {
        require(value, errors, "TestCases:", "缺少 TestCases 根节点");
        require(value, errors, "priority:", "测试用例缺少 priority");
        require(value, errors, "steps:", "测试用例缺少 steps");
        require(value, errors, "expected:", "测试用例缺少 expected");
        Matcher matcher = CASE_ID.matcher(value);
        Set<String> ids = new HashSet<>();
        int count = 0;
        while (matcher.find()) {
            count++;
            if (config.gates.requireUniqueCaseIds && !ids.add(matcher.group(1))) errors.add("caseId 重复: " + matcher.group(1));
        }
        if (count == 0) errors.add("至少需要一条带 caseId 的测试用例");
    }

    private static void validateApiSpec(String value, WorkspaceConfig config,
                                        InterfaceTarget expectedInterface, List<String> errors) {
        Map<?, ?> root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object loaded = new Yaml(new SafeConstructor(options)).load(value);
            if (!(loaded instanceof Map<?, ?> map)) {
                errors.add("接口生成规格必须是 YAML 对象");
                return;
            }
            root = map;
        } catch (RuntimeException exception) {
            errors.add("接口生成规格 YAML 无法解析: " + exception.getMessage());
            return;
        }
        if (!Integer.valueOf(2).equals(root.get("schemaVersion"))) errors.add("schemaVersion 必须为 2");
        requireText(root, "apiCode", errors, "缺少 apiCode");
        requireText(root, "description", errors, "缺少 description");
        requireText(root, "packageName", errors, "缺少 packageName");
        requireText(root, "className", errors, "缺少 className");
        requireText(root, "dataKey", errors, "缺少 dataKey");
        Object sourceCaseIds = root.get("sourceCaseIds");
        if (config.gates.requireTraceability && !nonEmptyCollection(sourceCaseIds)) {
            errors.add("缺少非空 sourceCaseIds 追溯");
        }
        Map<?, ?> target = childMap(root, "target", errors);
        String protocol = target == null ? "" : text(target.get("protocol"));
        if (!protocol.equals("HTTP") && !protocol.equals("DUBBO")) {
            errors.add("target.protocol 只能是 HTTP 或 DUBBO");
        }
        Map<?, ?> selector = childMap(root, "selector", errors);
        if (selector != null) {
            requireText(selector, "kind", errors, "selector.kind 不能为空");
            requireText(selector, "value", errors, "selector.value 不能为空");
        }
        if (expectedInterface != null) {
            if (!expectedInterface.protocol().equalsIgnoreCase(protocol)) {
                errors.add("target.protocol 与用户选择不一致，应为 " + expectedInterface.protocol());
            }
            String actualKind = selector == null ? "" : text(selector.get("kind"));
            String actualValue = selector == null ? "" : text(selector.get("value"));
            if (!expectedInterface.selectorKind().equals(actualKind)) {
                errors.add("selector.kind 与用户选择不一致，应为 " + expectedInterface.selectorKind());
            }
            if (!expectedInterface.selectorValue().trim().equals(actualValue)) {
                errors.add("selector.value 必须原样保留用户输入");
            }
            if (nonEmptyCollection(sourceCaseIds) && !expectedInterface.sourceCaseIds().isEmpty()) {
                Set<String> allowed = new HashSet<>(expectedInterface.sourceCaseIds());
                for (Object id : (Collection<?>) sourceCaseIds) {
                    if (!allowed.contains(text(id))) errors.add("sourceCaseIds 引用了输入用例中不存在的 caseId: " + id);
                }
            }
        }
        String selectorKind = selector == null ? "" : text(selector.get("kind"));
        if (protocol.equals("HTTP") && selectorKind.equals("DUBBO_METHOD")) {
            errors.add("HTTP 协议不能使用 DUBBO_METHOD 选择器");
        }
        if (protocol.equals("DUBBO") && selectorKind.equals("HTTP_PATH")) {
            errors.add("DUBBO 协议不能使用 HTTP_PATH 选择器");
        }
        Map<?, ?> generator = childMap(root, "generator", errors);
        String profile = generator == null ? "" : text(generator.get("profile"));
        String expectedProfile = protocol.equals("HTTP") ? "basetest-http"
                : protocol.equals("DUBBO") ? "basetest-dubbo" : "";
        if (!expectedProfile.isEmpty() && !expectedProfile.equals(profile)) {
            errors.add("generator.profile 与协议不匹配，应为 " + expectedProfile);
        }
        Map<?, ?> endpoint = childMap(root, "endpoint", errors);
        if (endpoint != null && protocol.equals("HTTP")) {
            requireText(endpoint, "method", errors, "HTTP endpoint.method 不能为空");
            String path = text(endpoint.get("path"));
            if (path.isBlank()) errors.add("HTTP endpoint.path 不能为空");
            else if (!path.startsWith("/")) errors.add("HTTP endpoint.path 必须以 / 开头");
        } else if (endpoint != null && protocol.equals("DUBBO")) {
            requireText(endpoint, "interfaceName", errors, "Dubbo endpoint.interfaceName 不能为空");
            requireText(endpoint, "dubboMethod", errors, "Dubbo endpoint.dubboMethod 不能为空");
            if (!nonEmptyCollection(endpoint.get("parameterTypes"))) {
                errors.add("Dubbo endpoint.parameterTypes 至少需要一个参数类型");
            }
        }
        if (!nonEmptyCollection(root.get("environments"))) errors.add("environments 至少需要一个环境");
        if (expectedInterface != null && endpoint != null) {
            String selected = expectedInterface.selectorValue().trim();
            if (protocol.equals("HTTP") && selectorKind.equals("HTTP_PATH")) {
                String[] parts = selected.split("\\s+", 2);
                if (parts.length != 2 || !parts[0].equalsIgnoreCase(text(endpoint.get("method")))
                        || !parts[1].equals(text(endpoint.get("path")))) {
                    errors.add("请明确输入 METHOD /path，endpoint 必须与输入方法和路径一致");
                }
            } else if (protocol.equals("DUBBO") && (selectorKind.equals("DUBBO_METHOD")
                    || selectorKind.equals("JAVA_SYMBOL"))) {
                String symbol = text(endpoint.get("interfaceName")) + "#" + text(endpoint.get("dubboMethod"));
                if (!selected.equals(symbol) && !selected.startsWith(symbol + "(")) {
                    errors.add("Dubbo endpoint 必须与输入接口类和方法一致");
                }
            } else if (expectedInterface.discoveredEndpoints().isEmpty()) {
                errors.add("该选择方式需要接口发现结果，请补充明确的 HTTP 方法路径或 Dubbo 方法签名");
            }
        }
        Object cases = root.get("cases");
        if (!nonEmptyCollection(cases)) {
            errors.add("cases 至少需要一条自动化用例");
        } else if (config.gates.requireTraceability) {
            Set<String> declared = new HashSet<>();
            if (sourceCaseIds instanceof Collection<?> ids) ids.forEach(id -> declared.add(text(id)));
            int index = 0;
            for (Object item : (Collection<?>) cases) {
                index++;
                if (!(item instanceof Map<?, ?> map) || text(map.get("sourceCaseId")).isBlank()) {
                    errors.add("cases[" + index + "] 缺少 sourceCaseId");
                } else {
                    if (!declared.contains(text(map.get("sourceCaseId")))) {
                        errors.add("cases[" + index + "].sourceCaseId 未在 sourceCaseIds 中声明");
                    }
                    requireText(map, "name", errors, "cases[" + index + "].name 不能为空");
                    requireText(map, "thought", errors, "cases[" + index + "].thought 不能为空");
                    requireText(map, "requestRef", errors, "cases[" + index + "].requestRef 不能为空");
                    requireText(map, "expectRef", errors, "cases[" + index + "].expectRef 不能为空");
                    if (!(map.get("request") instanceof Map)) errors.add("cases[" + index + "].request 必须是对象");
                    if (!(map.get("expect") instanceof Map)) errors.add("cases[" + index + "].expect 必须是对象");
                }
            }
        }
    }

    private static Map<?, ?> childMap(Map<?, ?> root, String key, List<String> errors) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) return map;
        errors.add("缺少 " + key + " 对象");
        return null;
    }

    private static void requireText(Map<?, ?> root, String key, List<String> errors, String message) {
        if (text(root.get(key)).isBlank()) errors.add(message);
    }

    private static boolean nonEmptyCollection(Object value) {
        return value instanceof Collection<?> collection && !collection.isEmpty();
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    public static List<String> extractCaseIds(String yaml) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = CASE_ID.matcher(yaml == null ? "" : yaml);
        while (matcher.find()) ids.add(matcher.group(1));
        return List.copyOf(ids);
    }

    private static void require(String value, List<String> errors, String token, String message) {
        if (!value.contains(token)) errors.add(message);
    }

    public record GateResult(boolean passed, List<String> errors) {}
    public record InterfaceTarget(String protocol, String selectorKind, String selectorValue,
                                  List<String> sourceCaseIds, List<DiscoveredEndpoint> discoveredEndpoints) {
        public InterfaceTarget(String protocol, String selectorKind, String selectorValue) {
            this(protocol, selectorKind, selectorValue, List.of(), List.of());
        }
        public InterfaceTarget {
            sourceCaseIds = sourceCaseIds == null ? List.of() : List.copyOf(sourceCaseIds);
            discoveredEndpoints = discoveredEndpoints == null ? List.of() : List.copyOf(discoveredEndpoints);
        }
    }
}
