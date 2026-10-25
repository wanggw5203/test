package io.testkit.basetest.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.testkit.basetest.config.ConfigLoader;
import java.util.List;
import java.util.Map;

public record ApiResponse(int status, Map<String, List<String>> headers, String body) {
  public ApiResponse {
    headers = Map.copyOf(headers);
  }

  public JsonNode json() {
    try {
      return ConfigLoader.jsonMapper().readTree(body);
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Response is not valid JSON");
    }
  }

  public <T> T as(Class<T> type) {
    try {
      return ConfigLoader.jsonMapper().findAndRegisterModules().readValue(body, type);
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Cannot decode response as " + type.getName());
    }
  }

  public ApiResponse assertStatus(int expected) {
    if (status != expected)
      throw new AssertionError("Expected HTTP " + expected + ", got " + status);
    return this;
  }

  @Override
  public String toString() {
    return "ApiResponse[status=" + status + ", body=<omitted>]";
  }
}
