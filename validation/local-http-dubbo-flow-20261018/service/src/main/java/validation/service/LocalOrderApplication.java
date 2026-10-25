package validation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import demo.golden.OrderService;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Standalone provider process, bound only to loopback. No test-framework dependency. */
public final class LocalOrderApplication {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path audit;
    private static synchronized void record(String protocol, Map<String,Object> request, OrderRules.Result result) {
        try {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("time", Instant.now().toString()); row.put("protocol", protocol);
            row.put("request", request); row.put("httpStatus", result.httpStatus()); row.put("response", result.body());
            Files.writeString(audit, JSON.writeValueAsString(row) + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) { throw new IllegalStateException("Cannot persist local audit", error); }
    }
    public static void main(String[] args) throws Exception {
        Map<String,String> options = new LinkedHashMap<>();
        for (String argument : args) {
            int eq = argument.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("Use --name=value arguments");
            options.put(argument.substring(0,eq), argument.substring(eq+1));
        }
        int httpPort = Integer.parseInt(options.getOrDefault("--http-port", "18081"));
        int dubboPort = Integer.parseInt(options.getOrDefault("--dubbo-port", "20891"));
        Path state = Path.of(options.getOrDefault("--state-dir", "runtime")).toAbsolutePath();
        Files.createDirectories(state); audit = state.resolve("service-requests.jsonl");
        OrderRules rules = new OrderRules();
        ServiceConfig<OrderService> provider = new ServiceConfig<>();
        ApplicationConfig application = new ApplicationConfig("local-order-provider");
        application.setQosEnable(false);
        // Dubbo treats 127.* as an auto-detected host; IPv6 loopback keeps the RPC listener local-only.
        application.setMetadataServiceProtocol("injvm");
        provider.setApplication(application);
        provider.setRegistry(new RegistryConfig("N/A"));
        ProtocolConfig protocol = new ProtocolConfig("dubbo", dubboPort);
        protocol.setHost("::1");
        provider.setProtocol(protocol);
        provider.setInterface(OrderService.class);
        provider.setRef(request -> {
            OrderRules.Result result = rules.create(request);
            record("DUBBO", request, result);
            return result.body();
        });
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        http.createContext("/health", exchange -> respond(exchange, 200, Map.of("status", "UP", "httpPort", httpPort, "dubboPort", dubboPort,
                "dubboInterface", OrderService.class.getName(), "faultyZero", rules.faulty(), "pid", ProcessHandle.current().pid())));
        http.createContext("/demo/orders", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/demo/orders")) { respond(exchange, 404, Map.of("code", "NOT_FOUND")); return; }
            if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, Map.of("code", "METHOD_NOT_ALLOWED")); return; }
            Map<String,Object> request;
            try { request = JSON.readValue(exchange.getRequestBody(), new TypeReference<>() {}); }
            catch (Exception invalid) { respond(exchange, 400, Map.of("code", "INVALID_JSON")); return; }
            OrderRules.Result result = rules.create(request); record("HTTP", request, result);
            respond(exchange, result.httpStatus(), result.body());
        });
        http.createContext("/admin/fault", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, Map.of("code", "METHOD_NOT_ALLOWED")); return; }
            try {
                Map<String,Object> value = JSON.readValue(exchange.getRequestBody(), new TypeReference<>() {});
                if (!(value.get("enabled") instanceof Boolean enabled)) { respond(exchange, 400, Map.of("code", "BOOLEAN_REQUIRED")); return; }
                rules.faulty(enabled); respond(exchange, 200, Map.of("faultyZero", rules.faulty()));
            } catch (Exception invalid) { respond(exchange, 400, Map.of("code", "INVALID_JSON")); }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { http.stop(0); provider.unexport(); }));
        try {
            provider.export();
            http.start();
            Files.writeString(state.resolve("ready.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "httpBaseUrl", "http://127.0.0.1:" + httpPort + "/", "dubboDirectUrl", "dubbo://[::1]:" + dubboPort,
                    "interface", OrderService.class.getName(), "method", "create", "parameterTypes", new String[]{"java.util.Map"}, "pid", ProcessHandle.current().pid())));
            System.out.println("READY HTTP http://127.0.0.1:" + httpPort + "/demo/orders ; RPC dubbo://[::1]:" + dubboPort + "/" + OrderService.class.getName());
            new CountDownLatch(1).await();
        } catch (Exception failure) { http.stop(0); provider.unexport(); throw failure; }
    }
    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }
}
