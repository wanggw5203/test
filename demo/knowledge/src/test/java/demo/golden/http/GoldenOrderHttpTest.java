package demo.golden.http;

import io.testkit.basetest.BaseTest;
import io.testkit.basetest.config.ConfigLoader;
import io.testkit.basetest.http.ApiClient;
import io.testkit.basetest.http.ApiResponse;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Generated from API contract golden_order_http_v1.
 * Source cases: GOLD-001, GOLD-002, GOLD-003.
 * @author AI
 * @create 2026-10-17
 */
public class GoldenOrderHttpTest extends BaseTest {
    private final ApiClient client = new ApiClient(
            System.getProperty("api.baseUrl", "http://localhost:8080/"));

    @DataProvider(name = "generatedCases")
    public Object[][] generatedCases() {
        return new Object[][] {
            {"GOLD-001", "test", "demo/golden/http/gold-001_req_test.json5", "demo/golden/http/gold-001_exp_test.json5"},
            {"GOLD-002", "test", "demo/golden/http/gold-002_req_test.json5", "demo/golden/http/gold-002_exp_test.json5"},
            {"GOLD-003", "test", "demo/golden/http/gold-003_req_test.json5", "demo/golden/http/gold-003_exp_test.json5"},
        };
    }

    @Test(dataProvider = "generatedCases", description = "黄金集演练：创建订单；未执行真实测试")
    public void generatedHttpApiTest(String sourceCaseId, String environment,
                                     String requestResource, String expectedResource) {
        Map<String, Object> request = readMap(requestResource);
        Map<String, Object> expected = readMap(expectedResource);
        ApiResponse actual = client.request("POST", "/demo/orders", Map.of(), request);
        assertHttpResponse(sourceCaseId, environment, expected, actual);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String resource) {
        return ConfigLoader.readResource(resource, Map.class);
    }

    private void assertHttpResponse(String sourceCaseId, String environment,
                                    Map<String, Object> expected, ApiResponse actual) {
        Object expectedStatus = expected.get("status");
        if (expectedStatus != null) {
            int status = expectedStatus instanceof Number
                    ? ((Number) expectedStatus).intValue()
                    : Integer.parseInt(String.valueOf(expectedStatus));
            Assert.assertEquals(actual.status(), status,
                    sourceCaseId + " [" + environment + "] HTTP status");
        }
        if (expected.containsKey("body")) {
            Assert.assertEquals(actual.json(),
                    ConfigLoader.jsonMapper().valueToTree(expected.get("body")),
                    sourceCaseId + " [" + environment + "] response body");
        } else if (!expected.containsKey("status")) {
            Assert.assertEquals(actual.json(),
                    ConfigLoader.jsonMapper().valueToTree(expected),
                    sourceCaseId + " [" + environment + "] response body");
        }
    }
}
