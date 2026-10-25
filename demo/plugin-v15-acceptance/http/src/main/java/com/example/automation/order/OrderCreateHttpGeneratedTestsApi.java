package com.example.automation.order;

import java.util.Map;
import io.testkit.basetest.http.ApiClient;
import io.testkit.basetest.http.ApiResponse;

public class OrderCreateHttpGeneratedTestsApi implements AutoCloseable {
    private final ApiClient client = new ApiClient(System.getProperty("api.baseUrl", "http://localhost:8080/"));
    public ApiResponse invoke(Map<String, Object> request) {
        return client.request("POST", "/api/orders", Map.ofEntries(Map.entry("X-Test-Source", "ai-test-flow")), request);
    }
    @Override public void close() { }
}
