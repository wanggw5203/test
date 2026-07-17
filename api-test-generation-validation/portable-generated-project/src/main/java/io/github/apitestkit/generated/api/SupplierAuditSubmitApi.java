package io.github.apitestkit.generated.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.apitestkit.generated.contract.ApiResponseVO;
import io.github.apitestkit.generated.contract.SupplierAuditSubmitRO;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class SupplierAuditSubmitApi {
    public static final String PATH = "/admin/third_employment/supplier/process/audit_submit";

    private final URI baseUri;
    private final Map<String, String> headers;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public SupplierAuditSubmitApi(URI baseUri, Map<String, String> headers) {
        this.baseUri = baseUri;
        this.headers = headers;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public ApiResponseVO<Boolean> auditSubmit(SupplierAuditSubmitRO requestBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(PATH))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)));
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), new TypeReference<ApiResponseVO<Boolean>>() {});
    }
}
