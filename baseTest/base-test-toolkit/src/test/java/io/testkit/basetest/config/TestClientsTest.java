package io.testkit.basetest.config;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.runtime.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestClientsTest {
  @Test
  public void selectedCapabilitiesDoNotRequireOtherPlatformSecrets() throws Exception {
    Path file = Files.createTempFile("selected-profile", ".yaml");
    try {
      Files.writeString(
          file,
          "common:\n  http.timeout.ms: 500\nenvironments:\n  qa:\n    http.base.url: ${QA_HTTP_URL}\n    kafka.bootstrap.servers: ${QA_KAFKA_BOOTSTRAP}\n");
      var config =
          EnvironmentConfig.load(
              file, "qa", Map.of("QA_HTTP_URL", "http://localhost:8080"), Map.of(), Set.of("http"));
      Assert.assertEquals(config.require("http.base.url"), "http://localhost:8080");
      Assert.assertFalse(config.values().containsKey("kafka.bootstrap.servers"));
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> EnvironmentConfig.load(file, "qa", Map.of(), Map.of(), Set.of("http")));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void factoriesUseConfiguredEndpointRuntimeAndLifecycle() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/health",
        exchange -> {
          byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    List<InvocationRecord> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
    var runtime = new AutomationRuntime(null, null, null, null, logs::add, null, null, null);
    var clients =
        new TestClients(
            new EnvironmentConfig(
                "qa",
                Map.of(
                    "http.base.url",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "http.timeout.ms",
                    "1000",
                    "sdk.timeout.ms",
                    "1000",
                    "sdk.concurrency",
                    "1",
                    "sdk.queue.capacity",
                    "2")),
            runtime);
    try {
      Assert.assertEquals(clients.http().request("GET", "/health", Map.of(), null).status(), 200);
      Assert.assertSame(clients.http(), clients.http());
      Assert.assertSame(clients.callSdk("runtime", RuntimeRegistry::current), runtime);
      Assert.assertEquals(logs.size(), 2);
      clients.close();
      clients.close();
      Assert.expectThrows(IllegalStateException.class, clients::http);
      Assert.expectThrows(IllegalStateException.class, () -> clients.callSdk("closed", () -> null));
    } finally {
      clients.close();
      server.stop(0);
    }
  }
}
