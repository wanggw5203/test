package io.github.apitestkit.generated;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.apitestkit.generated.api.SupplierAuditSubmitApi;
import io.github.apitestkit.generated.contract.ApiResponseVO;
import io.github.apitestkit.generated.contract.SupplierAuditSubmitRO;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SupplierAuditSubmitContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @DataProvider(name = "contractCases")
    public Object[][] contractCases() {
        return new Object[][] {{
                "01_contract_placeholder_req.json",
                "01_contract_placeholder_exp.json"
        }};
    }

    @Test(dataProvider = "contractCases")
    public void auditSubmit(String requestResource, String expectResource) throws Exception {
        String baseUrl = System.getenv("API_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new SkipException("API_BASE_URL未配置，契约脚手架仅完成编译验证");
        }
        SupplierAuditSubmitRO request = read(requestResource, SupplierAuditSubmitRO.class);
        ApiResponseVO<Boolean> expect = read(expectResource, new TypeReference<ApiResponseVO<Boolean>>() {});
        SupplierAuditSubmitApi api = new SupplierAuditSubmitApi(
                URI.create(baseUrl), Collections.emptyMap());
        ApiResponseVO<Boolean> actual = api.auditSubmit(request);
        Assert.assertEquals(actual.getCode(), expect.getCode());
        Assert.assertEquals(actual.getData(), expect.getData());
    }

    private <T> T read(String name, Class<T> type) throws Exception {
        try (InputStream input = resource(name)) { return mapper.readValue(input, type); }
    }

    private <T> T read(String name, TypeReference<T> type) throws Exception {
        try (InputStream input = resource(name)) { return mapper.readValue(input, type); }
    }

    private InputStream resource(String name) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(name);
        if (input == null) { throw new IllegalArgumentException("资源不存在: " + name); }
        return input;
    }
}
