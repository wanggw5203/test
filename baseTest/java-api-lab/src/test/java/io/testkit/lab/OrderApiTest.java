package io.testkit.lab;

import com.fasterxml.jackson.databind.JsonNode;
import io.testkit.basetest.BaseTest;
import io.testkit.basetest.ReturnValueContext;
import io.testkit.basetest.assertion.JsonDiff;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.data.DataDriveUtil;
import io.testkit.basetest.identity.IdentityContext;
import io.testkit.basetest.identity.UseIdentity;
import io.testkit.basetest.runtime.*;
import java.lang.reflect.Method;
import java.time.*;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.*;

public class OrderApiTest extends BaseTest implements RuntimeProvider {
  private AutomationRuntime runtime;

  @Override
  public AutomationRuntime automationRuntime() {
    return runtime;
  }

  private LabServer embedded;
  private ApiClient api;
  private final InMemoryResultPublisher results = new InMemoryResultPublisher();

  public record CreateOrder(String productId, Integer quantity, String couponCode) {}

  @BeforeClass
  public void setup() throws Exception {
    String url = environment().attribute("base.url").orElse("");
    if (url.isBlank()) {
      embedded = new LabServer(0, Clock.systemUTC(), Duration.ofMinutes(30));
      embedded.start();
      url = embedded.baseUrl();
    }
    api = new ApiClient(url);
    var report = new JsonLinesResultPublisher(java.nio.file.Path.of("target", "lab-results.jsonl"));
    runtime =
        new AutomationRuntime(
            new LoginIdentityProvider(api),
            null,
            new CompositeResultPublisher(results, report),
            null,
            report,
            null,
            null,
            null);
    Assert.assertEquals(api.call("GET", "/health", null, null).status(), 200);
  }

  @AfterClass(alwaysRun = true)
  public void teardown() {
    if (embedded != null) embedded.close();
  }

  @DataProvider(name = "orders")
  public Object[][] orders(Method method) {
    return DataDriveUtil.loadTestData(method, "orders");
  }

  @Test(dataProvider = "orders")
  @UseIdentity("alice")
  public void createReadDelete(CaseContext context, CreateOrder request, JsonNode expected) {
    Assert.assertEquals(currentCase().name(), context.name());
    String token = IdentityContext.requireCurrent().accessToken();
    ApiClient.Response created = api.call("POST", "/api/orders", token, request);
    Assert.assertEquals(created.status(), 201);
    String id = created.body().path("orderId").asText();
    Assert.assertFalse(id.isBlank());
    ReturnValueContext.put("orderId", id);
    try {
      var differences = JsonDiff.strict(expected, created.body(), "$.orderId");
      Assert.assertTrue(differences.isEmpty(), differences.toString());
      String savedId = ReturnValueContext.get("orderId", String.class).orElseThrow();
      ApiClient.Response fetched = api.call("GET", "/api/orders/" + savedId, token, null);
      Assert.assertEquals(fetched.status(), 200);
      Assert.assertEquals(fetched.body(), created.body());
    } finally {
      Assert.assertEquals(api.call("DELETE", "/api/orders/" + id, token, null).status(), 200);
    }
    assertError(api.call("GET", "/api/orders/" + id, token, null), 404, "ORDER_NOT_FOUND");
  }

  @DataProvider(name = "invalidOrders")
  public Object[][] invalidOrders() {
    return new Object[][] {
      {"{\"productId\":\"BOOK\",\"quantity\":0}", "INVALID_QUANTITY"},
      {"{\"productId\":\"BOOK\",\"quantity\":101}", "INVALID_QUANTITY"},
      {"{\"productId\":\"BOOK\",\"quantity\":\"1\"}", "INVALID_QUANTITY"},
      {"{\"productId\":\"BOOK\",\"quantity\":1.5}", "INVALID_QUANTITY"},
      {"{\"productId\":\"BOOK\"}", "INVALID_QUANTITY"},
      {"{\"productId\":\"UNKNOWN\",\"quantity\":1}", "INVALID_PRODUCT"},
      {"{\"productId\":\"BOOK\",\"quantity\":1,\"couponCode\":\"BAD\"}", "INVALID_COUPON"},
      {"{broken", "INVALID_JSON"},
      {"{} {}", "INVALID_JSON"}
    };
  }

  @Test(dataProvider = "invalidOrders")
  @UseIdentity("alice")
  public void rejectInvalidOrders(String body, String code) {
    assertError(
        api.call("POST", "/api/orders", IdentityContext.requireCurrent().accessToken(), body),
        400,
        code);
  }

  @Test
  public void rejectMissingAndForgedTokens() {
    assertError(api.call("GET", "/api/me", null, null), 401, "UNAUTHORIZED");
    assertError(api.call("GET", "/api/me", "forged-token", null), 401, "INVALID_TOKEN");
  }

  @Test
  public void rejectWrongPassword() {
    assertError(
        api.call("POST", "/api/auth/login", null, Map.of("username", "alice", "password", "wrong")),
        401,
        "INVALID_CREDENTIALS");
  }

  @Test
  public void logoutRevokesToken() {
    String token = api.login("alice");
    try {
      Assert.assertEquals(
          api.call("GET", "/api/me", token, null).body().path("username").asText(), "alice");
      Assert.assertEquals(api.call("POST", "/api/auth/logout", token, null).status(), 200);
      assertError(api.call("GET", "/api/me", token, null), 401, "INVALID_TOKEN");
    } finally {
      api.call("POST", "/api/auth/logout", token, null);
    }
  }

  @Test
  @UseIdentity("alice")
  public void rejectOtherOwnerAndNonAdmin() {
    String alice = IdentityContext.requireCurrent().accessToken();
    assertError(api.call("GET", "/api/admin/stats", alice, null), 403, "FORBIDDEN");
    ApiClient.Response created =
        api.call("POST", "/api/orders", alice, new CreateOrder("BOOK", 1, null));
    Assert.assertEquals(created.status(), 201);
    String path = "/api/orders/" + created.body().path("orderId").asText();
    String bob = null;
    String admin = null;
    try {
      bob = api.login("bob");
      admin = api.login("admin");
      assertError(api.call("GET", path, bob, null), 403, "FORBIDDEN");
      assertError(api.call("DELETE", path, bob, null), 403, "FORBIDDEN");
      Assert.assertEquals(api.call("GET", path, alice, null).status(), 200);
      Assert.assertEquals(api.call("GET", path, admin, null).status(), 200);
    } finally {
      api.call("DELETE", path, alice, null);
      if (bob != null) api.call("POST", "/api/auth/logout", bob, null);
      if (admin != null) api.call("POST", "/api/auth/logout", admin, null);
    }
  }

  @Test
  @UseIdentity("admin")
  public void adminCanReadStats() {
    ApiClient.Response response =
        api.call("GET", "/api/admin/stats", IdentityContext.requireCurrent().accessToken(), null);
    Assert.assertEquals(response.status(), 200);
    Assert.assertTrue(response.body().path("orderCount").isIntegralNumber());
  }

  @Test(dependsOnMethods = "createReadDelete")
  public void listenerPublishedAndContextsCleared() {
    Assert.assertTrue(IdentityContext.current().isEmpty());
    Assert.assertTrue(ReturnValueContext.get("orderId").isEmpty());
    Assert.assertTrue(
        results.results().stream().filter(r -> r.name().startsWith("order-")).count() >= 3);
    Assert.assertTrue(
        results.results().stream()
            .filter(r -> r.name().startsWith("order-"))
            .allMatch(r -> r.status() == TestResultRecord.Status.PASSED));
  }

  static void assertError(ApiClient.Response actual, int status, String code) {
    Assert.assertEquals(actual.status(), status);
    Assert.assertEquals(actual.body().path("code").asText(), code);
  }
}
