package io.github.aitestflow.service;

import io.github.aitestflow.model.PipelineStage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无模型演练生成器，只验证插件链路、项目文件写入和 Git 变更。
 * 输出故意带有醒目标记，不能替代真实模型的测试分析。
 */
public final class DemoGenerator {
    private static final Pattern RULE_PATTERN = Pattern.compile("BR-(\\d{3}).*?[：:]\\s*(.+)");
    private static final Pattern CASE_ID_PATTERN = Pattern.compile("(?m)^\\s*-?\\s*caseId:\\s*[\"']?([^\\s\"']+)");
    private static final Pattern JSON_PROTOCOL = Pattern.compile("\\\"protocol\\\"\\s*:\\s*\\\"(HTTP|DUBBO)\\\"");
    private static final Pattern JSON_SELECTOR_KIND = Pattern.compile("\\\"selectorKind\\\"\\s*:\\s*\\\"([A-Z_]+)\\\"");
    private static final Pattern JSON_SELECTOR_VALUE = Pattern.compile("\\\"value\\\"\\s*:\\s*\\\"([^\"]+)\\\"");
    private static final Pattern HTTP_SELECTOR = Pattern.compile("(?i)^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\s+(.+)$");

    private DemoGenerator() {}

    public static String generate(PipelineStage stage, String input) {
        return generate(stage, input, "");
    }

    public static String generate(PipelineStage stage, String input, String evidence) {
        return switch (stage) {
            case REQUIREMENT_TO_TAD -> tad(input);
            case TAD_TO_TEST_CASES -> testCases(input);
            case TEST_CASES_TO_WEB_ATC -> webAtc(input);
            case TEST_CASES_TO_API_SPEC -> apiSpec(input, evidence);
            case FEEDBACK -> feedback();
        };
    }

    private static String tad(String input) {
        List<String> rules = extractRules(input);
        String title = extractTitle(input);
        StringBuilder output = new StringBuilder();
        output.append("# ").append(title).append("\n\n")
                .append("> 演练模式产物：用于验证插件流程与项目文件写入，配置真实模型后请重新生成。\n\n")
                .append("# 业务背景\n\n")
                .append("## 1. 背景说明\n\n")
                .append("根据导入的原始需求，对业务规则、流程、依赖和待确认项进行结构化整理。\n\n")
                .append("# TRD 测试需求分析文档\n\n")
                .append("## 1. 业务流程\n\n")
                .append("```mermaid\nflowchart TD\n    A[用户进入业务入口] --> B{是否满足业务条件}\n    B -->|是| C[执行业务动作]\n    B -->|否| D[展示对应提示]\n    C --> E[产生可观察业务结果]\n```\n\n")
                .append("## 2. 业务规则\n\n");
        for (int i = 0; i < rules.size(); i++) {
            output.append(i + 1).append(". **BR-").append(String.format("%03d", i + 1))
                    .append(" 原始规则**：").append(rules.get(i)).append("\n");
        }
        output.append("\n## 3. 待确认项\n\n")
                .append("- 演练模式不判断需求冲突；需要真实模型结合 Skill 重新分析。\n")
                .append("- 原始材料未明确的接口、状态和异常处理不得作为最终结论。\n\n")
                .append("# STD 系统测试分析文档\n\n")
                .append("## 1. 技术实现证据\n\n")
                .append("演练模式未读取代码实现证据，接口、数据库、缓存和调用链均待真实分析。\n");
        return output.toString();
    }

    private static String testCases(String tad) {
        List<String> rules = new ArrayList<>();
        for (String line : tad.split("\\R")) {
            Matcher matcher = RULE_PATTERN.matcher(line.replace("**", ""));
            if (matcher.find()) rules.add(matcher.group(2).trim());
        }
        if (rules.isEmpty()) rules = extractRules(tad);
        StringBuilder yaml = new StringBuilder("# 演练模式产物：配置真实模型后请重新生成\nTestCases:\n");
        for (int i = 0; i < Math.min(rules.size(), 20); i++) {
            String id = String.format("DEMO-%03d", i + 1);
            String rule = yamlQuote(rules.get(i));
            yaml.append("  - caseId: ").append(id).append('\n')
                    .append("    title: \"").append(rule).append("\"\n")
                    .append("    priority: P1\n")
                    .append("    tags: [演练, 待真实模型生成]\n")
                    .append("    thought: \"覆盖 BR-").append(String.format("%03d", i + 1)).append("\"\n")
                    .append("    preconditions:\n      - \"准备满足该规则的业务上下文\"\n")
                    .append("    steps:\n      - action: \"执行规则对应的业务操作\"\n")
                    .append("        expected: \"产生规则描述的可观察结果\"\n");
        }
        return yaml.toString();
    }

    private static String apiSpec(String input, String evidence) {
        String protocol = firstGroup(JSON_PROTOCOL, evidence, "HTTP");
        String selectorKind = firstGroup(JSON_SELECTOR_KIND, evidence,
                "DUBBO".equals(protocol) ? "DUBBO_METHOD" : "HTTP_PATH");
        String selector = unescapeJson(firstGroup(JSON_SELECTOR_VALUE, evidence,
                ""));
        List<String> caseIds = extractCaseIds(input);
        String apiCode = apiCode(selector, protocol);
        String className = className(apiCode) + "ApiTest";
        String packageName = "generated.apitest." + apiCode;
        StringBuilder yaml = new StringBuilder();
        yaml.append("# 演练模式产物：接口、类型与期望值必须在评审后替换为真实项目证据\n")
                .append("schemaVersion: 2\n")
                .append("apiCode: ").append(apiCode).append('\n')
                .append("sourceCaseIds:\n");
        caseIds.forEach(id -> yaml.append("  - ").append(id).append('\n'));
        yaml.append("description: \"演练模式 ").append(protocol).append(" 接口自动化\"\n")
                .append("packageName: ").append(packageName).append('\n')
                .append("resourcePackage: ").append(packageName).append('\n')
                .append("className: ").append(className).append('\n')
                .append("dataKey: ").append(apiCode).append("_cases\n")
                .append("environments: [test]\n")
                .append("target:\n  protocol: ").append(protocol).append("\n  module: \"\"\n")
                .append("selector:\n  kind: ").append(selectorKind)
                .append("\n  value: \"").append(yamlQuote(selector)).append("\"\n")
                .append("endpoint:\n");
        if ("DUBBO".equals(protocol)) {
            DubboTarget endpoint = dubboEndpoint(selectorKind, selector);
            yaml.append("  interfaceName: \"").append(yamlQuote(endpoint.interfaceName())).append("\"\n")
                    .append("  dubboMethod: \"").append(yamlQuote(endpoint.method())).append("\"\n")
                    .append("  parameterTypes: [")
                    .append(String.join(", ", endpoint.parameterTypes())).append("]\n")
                    .append("  attachments: {}\n")
                    .append("generator:\n  profile: basetest-dubbo\n");
        } else {
            Matcher http = HTTP_SELECTOR.matcher(selector.trim());
            boolean explicitHttpEndpoint = "HTTP_PATH".equals(selectorKind) && http.matches();
            String method = explicitHttpEndpoint ? http.group(1).toUpperCase() : "";
            String path = explicitHttpEndpoint ? http.group(2).trim() : "";
            yaml.append("  method: \"").append(method).append("\"\n")
                    .append("  path: \"").append(yamlQuote(path)).append("\"\n")
                    .append("  authenticated: false\n")
                    .append("  headers: {}\n")
                    .append("generator:\n  profile: basetest-http\n");
        }
        yaml.append("types:\n  assets: []\n")
                .append("evidence: []\n")
                .append("cases:\n");
        for (int index = 0; index < caseIds.size(); index++) {
            String id = caseIds.get(index);
            String ref = apiCode + "_" + String.format("%03d", index + 1);
            yaml.append("  - sourceCaseId: ").append(id).append('\n')
                    .append("    name: \"").append(id).append(" 演练用例\"\n")
                    .append("    thought: \"仅验证插件与生成器链路；业务输入和期望待真实证据补充\"\n")
                    .append("    requestRef: ").append(ref).append("_request.json5\n")
                    .append("    expectRef: ").append(ref).append("_expect.json5\n")
                    .append("    request:\n      base:\n        demo: true\n")
                    .append("    expect:\n      base:\n        status: pending-evidence\n");
        }
        return yaml.toString();
    }

    private static List<String> extractCaseIds(String input) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = CASE_ID_PATTERN.matcher(input == null ? "" : input);
        while (matcher.find() && ids.size() < 20) ids.add(matcher.group(1));
        if (ids.isEmpty()) ids.add("DEMO-001");
        return ids;
    }

    private static String firstGroup(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String unescapeJson(String value) {
        return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String apiCode(String selector, String protocol) {
        String normalized = selector.toLowerCase().replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) normalized = "demo_api";
        if (Character.isDigit(normalized.charAt(0))) normalized = "api_" + normalized;
        if (normalized.length() > 48) normalized = normalized.substring(normalized.length() - 48);
        return ("DUBBO".equals(protocol) ? "dubbo_" : "http_") + normalized;
    }

    private static String className(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.split("_+")) {
            if (part.isBlank()) continue;
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? "Demo" : out.toString();
    }

    private static DubboTarget dubboEndpoint(String selectorKind, String selector) {
        if (!"DUBBO_METHOD".equals(selectorKind) && !"JAVA_SYMBOL".equals(selectorKind)) {
            return new DubboTarget("", "", List.of());
        }
        String value = selector.trim();
        int hash = value.indexOf('#');
        int open = value.indexOf('(', hash + 1);
        int close = value.endsWith(")") ? value.length() - 1 : -1;
        if (hash > 0 && open > hash + 1 && close >= open) {
            String parameters = value.substring(open + 1, close).trim();
            List<String> parameterTypes = parameters.isEmpty() ? List.of()
                    : Arrays.stream(parameters.split(",")).map(String::trim)
                    .filter(item -> !item.isBlank()).toList();
            return new DubboTarget(value.substring(0, hash), value.substring(hash + 1, open), parameterTypes);
        }
        return new DubboTarget("", "", List.of());
    }

    private record DubboTarget(String interfaceName, String method, List<String> parameterTypes) { }

    private static String webAtc(String input) {
        return "# 演练模式占位 ATC，不猜测页面定位器\n"
                + "suite: demo\n"
                + "status: pending-page-evidence\n"
                + "TestCases:\n"
                + "  - caseId: DEMO-001\n"
                + "    steps: []\n"
                + "    blockers:\n"
                + "      - 需要真实页面、黄金脚本和执行器证据\n";
    }

    private static String feedback() {
        return "feedback:\n  status: blocked\n  classification: UNKNOWN\n  remainingBlockers:\n    - 演练模式未执行真实用例\n";
    }

    private static List<String> extractRules(String input) {
        List<String> rules = new ArrayList<>();
        for (String raw : input.split("\\R")) {
            String line = raw.trim().replaceFirst("^[-*+]\\s+", "");
            if (line.isBlank() || line.startsWith("#") || line.startsWith("```") || line.startsWith(">")) continue;
            if (line.length() < 8) continue;
            rules.add(line.length() > 180 ? line.substring(0, 180) + "…" : line);
            if (rules.size() == 20) break;
        }
        if (rules.isEmpty()) rules.add("原始需求中未提取到可演练规则，请配置真实模型重新分析");
        return rules;
    }

    private static String extractTitle(String input) {
        for (String line : input.split("\\R")) {
            String value = line.trim();
            if (value.startsWith("#")) return value.replaceFirst("^#+\\s*", "");
        }
        return "结构化测试分析";
    }

    private static String yamlQuote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
