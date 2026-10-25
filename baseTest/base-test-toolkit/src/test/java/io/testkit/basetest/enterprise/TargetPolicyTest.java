package io.testkit.basetest.enterprise;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.http.ApiClient;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TargetPolicyTest {
  @Test
  public void shouldRejectMalformedEnvironmentServiceKeysAndPorts() {
    for (String key :
        List.of("qa/.orders", ".qa/orders", "qa/" + "a".repeat(65), "qa/orders/api")) {
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> new TargetPolicy(Map.of(key, Set.of("https://orders.example.com"))));
    }
    for (String origin :
        List.of("http://orders.example.com:0", "http://orders.example.com:65536")) {
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> new TargetPolicy(Map.of("qa/orders", Set.of(origin))));
    }
  }

  @Test
  public void deniedOriginNeverReachesHttpServerAndApprovedOriginSucceeds() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new AtomicInteger();
    server.createContext(
        "/",
        exchange -> {
          calls.incrementAndGet();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    var api = new ApiClient(url);
    var definition =
        new CaseDefinition("orders.read", "owner", "orders", "orders-api", CaseLevel.SMOKE, false);
    var run = new RunMetadata("r", "w", "qa", "orders");
    try {
      var denied = new EnterpriseExecution(run, ExecutionPolicy.localSafe(), new Shard(0, 1));
      try (var scope =
          EnterpriseContext.use(new EnterpriseContext.Snapshot(denied, definition, "one"))) {
        Assert.expectThrows(
            IllegalArgumentException.class, () -> api.request("GET", "/", Map.of(), null));
      }
      Assert.assertEquals(calls.get(), 0);
      var policy =
          new ExecutionPolicy(
              Set.of("qa"),
              Set.of("orders-api"),
              false,
              new TargetPolicy(Map.of("qa/orders-api", Set.of(url))));
      try (var scope =
          EnterpriseContext.use(
              new EnterpriseContext.Snapshot(
                  new EnterpriseExecution(run, policy, new Shard(0, 1)), definition, "two"))) {
        api.request("GET", "/", Map.of(), null).assertStatus(200);
      }
      Assert.assertEquals(calls.get(), 1);
      Assert.expectThrows(
          IllegalArgumentException.class,
          () ->
              policy
                  .targets()
                  .requireHttp(
                      new RunMetadata("r", "w", "dev", "orders"), definition, URI.create(url)));
    } finally {
      server.stop(0);
    }
  }
}
