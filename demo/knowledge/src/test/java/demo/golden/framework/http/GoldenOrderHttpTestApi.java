package demo.golden.framework.http;

import java.util.Map;
import io.testkit.basetest.http.ApiClient;
import io.testkit.basetest.http.ApiResponse;

public class GoldenOrderHttpTestApi implements AutoCloseable {
    private final ApiClient client = new ApiClient(System.getProperty("api.baseUrl", "http://localhost:8080/"));
    public ApiResponse invoke(Map<String, Object> request) {
        return client.request("POST", "/demo/orders", Map.of(), request);
    }
    @Override public void close() { }
}
