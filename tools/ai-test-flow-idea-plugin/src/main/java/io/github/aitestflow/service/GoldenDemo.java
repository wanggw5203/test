package io.github.aitestflow.service;

import io.github.aitestflow.model.PipelineStage;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, offline model responses. These fixtures do not represent execution results. */
public final class GoldenDemo {
    public static final String ID = "order-create-v1";
    public static final String HTTP_SELECTOR = "POST /demo/orders";
    public static final String DUBBO_SELECTOR = "demo.golden.OrderService#create(java.util.Map)";

    private GoldenDemo() { }

    public static String requirement() { return resource("prd.md"); }

    public static String evidence(boolean dubbo) {
        return "黄金集: " + ID + "\n仅用于离线资产流程验证；所有接口与页面均为合成演练契约。\n"
                + "protocol: " + (dubbo ? "DUBBO" : "HTTP") + "\nselector: "
                + (dubbo ? DUBBO_SELECTOR : HTTP_SELECTOR)
                + "\n未执行真实接口、浏览器或 TestNG。\n";
    }

    public static String generate(PipelineStage stage, String evidence) {
        boolean dubbo = evidence != null && evidence.matches("(?s).*\\\"protocol\\\"\\s*:\\s*\\\"DUBBO\\\".*");
        return switch (stage) {
            case REQUIREMENT_TO_TAD -> resource("tad.md");
            case TAD_TO_TEST_CASES -> resource("testcases.yaml");
            case TEST_CASES_TO_API_SPEC -> apiSpec(dubbo);
            case TEST_CASES_TO_WEB_ATC -> resource("web.atc.yaml");
            case FEEDBACK -> resource("feedback.yaml");
        };
    }

    private static String apiSpec(boolean dubbo) {
        String protocol = dubbo ? "DUBBO" : "HTTP";
        String apiCode = dubbo ? "golden_order_dubbo_framework_v3" : "golden_order_http_framework_v3";
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 2);
        root.put("apiCode", apiCode);
        root.put("sourceCaseIds", List.of("GOLD-001", "GOLD-002", "GOLD-003"));
        root.put("description", "黄金集演练：创建订单；未执行真实测试");
        root.put("packageName", "demo.golden.framework.v3." + (dubbo ? "dubbo" : "http"));
        root.put("className", dubbo ? "GoldenOrderDubboTest" : "GoldenOrderHttpTest");
        root.put("dataKey", apiCode);
        root.put("environments", List.of("test"));
        root.put("target", Map.of("protocol", protocol));
        root.put("selector", Map.of("kind", dubbo ? "DUBBO_METHOD" : "HTTP_PATH",
                "value", dubbo ? DUBBO_SELECTOR : HTTP_SELECTOR));
        root.put("endpoint", dubbo
                ? Map.of("interfaceName", "demo.golden.OrderService", "dubboMethod", "create",
                "parameterTypes", List.of("java.util.Map"))
                : Map.of("method", "POST", "path", "/demo/orders", "authenticated", false));
        root.put("generator", Map.of("profile", dubbo ? "basetest-dubbo" : "basetest-http"));
        root.put("types", Map.of("assets", List.of()));
        root.put("evidence", List.of());
        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(testCase("GOLD-001", "正常创建订单", Map.of("sku", "SKU-DEMO", "quantity", 1),
                201, Map.of("code", "OK", "orderId", "ORDER-DEMO-001"), dubbo));
        cases.add(testCase("GOLD-002", "数量为零", Map.of("sku", "SKU-DEMO", "quantity", 0),
                400, Map.of("code", "INVALID_QUANTITY"), dubbo));
        cases.add(testCase("GOLD-003", "缺少商品编码", Map.of("quantity", 1),
                400, Map.of("code", "SKU_REQUIRED"), dubbo));
        root.put("cases", cases);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        return "# GOLDEN DEMO " + ID + "：合成演练契约，未执行真实测试\n" + new Yaml(options).dump(stable(root));
    }

    private static Object stable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), stable(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> values = new ArrayList<>();
            list.forEach(item -> values.add(stable(item)));
            return values;
        }
        return value;
    }

    private static Map<String, Object> testCase(String id, String name, Map<String, Object> request,
                                                int status, Map<String, Object> body, boolean dubbo) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceCaseId", id);
        item.put("name", name);
        item.put("thought", "黄金集 " + ID + "，追溯 " + id);
        item.put("requestRef", id.toLowerCase() + ".json5");
        item.put("expectRef", id.toLowerCase() + ".json5");
        item.put("request", Map.of("base", request));
        item.put("expect", Map.of("base", dubbo ? body : Map.of("status", status, "body", body)));
        return item;
    }

    public static String resource(String filename) {
        try (InputStream input = GoldenDemo.class.getResourceAsStream("/golden-demo/" + ID + "/" + filename)) {
            if (input == null) throw new IOException("黄金集资源缺失：" + filename);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
}
