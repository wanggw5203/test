package io.testkit.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.runtime.RuntimeRegistry;
import java.time.Duration;
import java.util.Map;

/** Business adapter: HTTP transport is supplied by base-test-toolkit. */
public final class ApiClient {
  static final ObjectMapper JSON = new ObjectMapper();
  private final io.testkit.basetest.http.ApiClient client;

  public record Response(int status, JsonNode body) {}

  public ApiClient(String baseUrl) {
    client =
        new io.testkit.basetest.http.ApiClient(
            baseUrl,
            Duration.ofSeconds(10),
            record -> RuntimeRegistry.current().invocationLogs().accept(record));
  }

  public Response call(String method, String path, String token, Object body) {
    var result =
        client.request(
            method,
            path,
            token == null ? Map.of() : Map.of("Authorization", "Bearer " + token),
            body);
    return new Response(result.status(), result.json());
  }

  public String login(String username) {
    Response result =
        call(
            "POST",
            "/api/auth/login",
            null,
            Map.of("username", username, "password", username + "123"));
    if (result.status() != 200 || !result.body().path("accessToken").isTextual())
      throw new IllegalStateException("Login failed for " + username + ", HTTP " + result.status());
    return result.body().path("accessToken").asText();
  }
}
