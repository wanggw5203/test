package com.example.automation.api;
import io.testkit.basetest.http.ApiClient;
import io.testkit.basetest.http.ApiResponse;
import com.example.automation.config.ProjectSettings;
import java.util.Map;
/** Base transport; generate business-specific API classes through the plugin. */
public final class HttpApi {
    public ApiResponse invoke(String method, String path, Map<String,String> headers, Object body) {
        return new ApiClient(ProjectSettings.value("api.baseUrl")).request(method, path, headers, body);
    }
}
