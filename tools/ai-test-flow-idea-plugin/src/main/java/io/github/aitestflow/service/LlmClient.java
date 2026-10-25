package io.github.aitestflow.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.settings.AiTestFlowSettings;
import io.github.aitestflow.settings.ApiKeyStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class LlmClient {
    private final Project project;

    public LlmClient(Project project) {
        this.project = project;
    }

    public String execute(PipelineStage stage, String skillContext, String input, String evidence,
                          List<RequirementImporter.ImageAttachment> images) throws IOException, InterruptedException {
        var settings = AiTestFlowSettings.getInstance(project).data();
        if (settings.demoMode) {
            if (!GoldenDemo.ID.equals(settings.goldenDataset)) {
                throw new IOException("未知黄金集配置：" + settings.goldenDataset);
            }
            return GoldenDemo.generate(stage, evidence);
        }
        String key = ApiKeyStore.get(project);
        return executeRemote(stage, skillContext, input, evidence, images, settings, key, project.getName());
    }

    static String executeRemote(PipelineStage stage, String skillContext, String input, String evidence,
                                List<RequirementImporter.ImageAttachment> images,
                                AiTestFlowSettings.StateData settings, String key, String projectName)
            throws IOException, InterruptedException {
        if (key.isBlank()) {
            throw new IOException("未配置 API Key，请打开 Settings | Tools | AI Test Flow");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model);
        body.addProperty("temperature", settings.temperature);
        JsonArray messages = new JsonArray();
        messages.add(message("system", "你是测试工程工作流执行器。严格遵循下面的 Skill 与证据边界；不得虚构缺失信息。\n" + skillContext));
        String user = stage.instruction()
                + "\n\n【项目】\n" + projectName
                + "\n\n【输入产物】\n" + input
                + (evidence == null || evidence.isBlank() ? "" : "\n\n【补充证据】\n" + evidence);
        messages.add(userMessage(user, images));
        body.add("messages", messages);

        String endpoint = normalizeEndpoint(settings.baseUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .header("X-AI-Test-Stage", stage.name())
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = sendWithRetry(client, request);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("模型调用失败 HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 1200));
        }
        return parseContent(response.body());
    }

    private static HttpResponse<String> sendWithRetry(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status != 429 && status < 500) return response;
            if (attempt == 3) return response;
            long retryAfterSeconds = response.headers().firstValue("Retry-After")
                    .flatMap(LlmClient::parsePositiveLong).orElse((long) attempt * 2L);
            Thread.sleep(Math.min(retryAfterSeconds, 30L) * 1000L);
        }
        return response;
    }

    private static java.util.Optional<Long> parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? java.util.Optional.of(parsed) : java.util.Optional.empty();
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    static String parseContent(String responseBody) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException exception) {
            throw new IOException("模型响应缺少 choices[0].message.content", exception);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private static JsonObject userMessage(String text, List<RequirementImporter.ImageAttachment> images) {
        if (images == null || images.isEmpty()) return message("user", text);
        JsonObject object = new JsonObject();
        object.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", text);
        content.add(textPart);
        for (RequirementImporter.ImageAttachment attachment : images) {
            JsonObject imagePart = new JsonObject();
            imagePart.addProperty("type", "image_url");
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", attachment.dataUrl());
            imageUrl.addProperty("detail", "high");
            imagePart.add("image_url", imageUrl);
            content.add(imagePart);
        }
        object.add("content", content);
        return object;
    }

    private static String normalizeEndpoint(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.endsWith("/chat/completions") ? value : value + "/chat/completions";
    }

    private static String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "…";
    }
}
