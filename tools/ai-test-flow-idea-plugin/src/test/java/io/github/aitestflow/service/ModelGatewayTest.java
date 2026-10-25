package io.github.aitestflow.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.settings.AiTestFlowSettings;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.junit.jupiter.api.Assertions.*;

class ModelGatewayTest {
    @Test void remoteModelTransportStillWorksForEveryStageWhenGoldenModeIsOff() throws Exception {
        var received = new ConcurrentLinkedQueue<String>();
        HttpServer gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/v1/chat/completions", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            String stage = exchange.getRequestHeaders().getFirst("X-AI-Test-Stage");
            received.add(stage + ":" + request.get("model").getAsString() + ":"
                    + request.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
            String response = new Gson().toJson(java.util.Map.of("choices", List.of(java.util.Map.of(
                    "message", java.util.Map.of("content", "gateway-response:" + stage)))));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        gateway.start();
        try {
            var configuration = new AiTestFlowSettings.StateData();
            configuration.demoMode = false;
            configuration.baseUrl = "http://127.0.0.1:" + gateway.getAddress().getPort() + "/v1";
            configuration.model = "enterprise-test-model";
            for (PipelineStage stage : PipelineStage.values()) {
                String result = LlmClient.executeRemote(stage, "test-skill-context", "input", "evidence",
                        List.of(), configuration, "synthetic-test-key", "gateway-test-project");
                assertEquals("gateway-response:" + stage.name(), result);
            }
            assertEquals(PipelineStage.values().length, received.size());
            assertTrue(received.stream().allMatch(value -> value.contains("enterprise-test-model")
                    && value.contains("test-skill-context")));
        } finally { gateway.stop(0); }
    }
}
