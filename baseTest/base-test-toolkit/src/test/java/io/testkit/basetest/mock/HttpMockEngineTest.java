package io.testkit.basetest.mock;

import io.testkit.basetest.http.ApiClient;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HttpMockEngineTest {
  @Test
  public void actualHttpBodyMatchCallCountAndFailureCleanup() {
    var rule =
        new MockRule(
            "http",
            "/inventory",
            Map.of("method", "POST", "body", Map.of("sku", "BOOK")),
            Map.of("available", true),
            1);
    var engine = new HttpMockEngine(Map.of("inventory", List.of(rule)));
    try (var session = engine.open(new MockScenario("inventory", MockScope.CASE, List.of()))) {
      var api = new ApiClient(engine.currentBaseUrl());
      api.request("POST", "/inventory", Map.of(), Map.of("sku", "OTHER")).assertStatus(404);
      Assert.assertTrue(
          api.request("POST", "/inventory", Map.of(), Map.of("sku", "BOOK"))
              .assertStatus(200)
              .json()
              .path("available")
              .asBoolean());
      api.request("POST", "/inventory", Map.of(), Map.of("sku", "BOOK")).assertStatus(404);
    }
    var missing = engine.open(new MockScenario("inventory", MockScope.CASE, List.of()));
    Assert.expectThrows(IllegalStateException.class, missing::close);
    Assert.assertFalse(missing.active());
    Assert.expectThrows(IllegalStateException.class, engine::currentBaseUrl);
  }
}
