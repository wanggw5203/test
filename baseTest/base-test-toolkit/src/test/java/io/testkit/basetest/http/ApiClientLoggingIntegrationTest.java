package io.testkit.basetest.http;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.runtime.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiClientLoggingIntegrationTest {
  @Test
  public void defaultClientLogsRealBodiesWithoutChangingTheWireOrLeakingCredentials()
      throws Exception {
    Path file = Files.createTempDirectory("http-wire-log").resolve("calls.jsonl");
    Map<String, String> previous = new HashMap<>();
    for (String key :
        List.of(
            "testkit.log.enabled", "testkit.log.console", "testkit.log.bodies", "testkit.log.file"))
      previous.put(key, System.getProperty(key));
    AtomicReference<String> received = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/orders",
        exchange -> {
          received.set(
              new String(
                  exchange.getRequestBody().readAllBytes(),
                  java.nio.charset.StandardCharsets.UTF_8));
          exchange.getResponseHeaders().add("Set-Cookie", "session=RawSessionSecret");
          byte[] body =
              "{\"code\":\"OK\",\"token\":\"RawResponseSecret\"}"
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(201, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      System.setProperty("testkit.log.enabled", "true");
      System.setProperty("testkit.log.console", "false");
      System.setProperty("testkit.log.bodies", "true");
      System.setProperty("testkit.log.file", file.toString());
      List<InvocationRecord> metrics = new ArrayList<>();
      try (var scope =
          RuntimeRegistry.use(
              new AutomationRuntime(null, null, null, null, metrics::add, null, null, null))) {
        var client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
        var result =
            client.request(
                "POST",
                "/orders?token=RawQuerySecret",
                Map.of("Authorization", "Bearer RawHeaderSecret"),
                Map.of("sku", "SKU-001", "password", "RawRequestSecret"));
        Assert.assertEquals(result.status(), 201);
        Assert.assertEquals(result.json().path("token").asText(), "RawResponseSecret");
        Assert.assertTrue(
            received.get().contains("RawRequestSecret"),
            "Logging must never redact or mutate the actual request");
        Assert.assertEquals(metrics.size(), 1, "Default client must use the current runtime sink");
      }
      String text = Files.readString(file);
      Assert.assertTrue(text.contains("SKU-001"));
      Assert.assertTrue(text.contains("201"));
      for (String secret :
          List.of(
              "RawRequestSecret",
              "RawResponseSecret",
              "RawQuerySecret",
              "RawSessionSecret",
              "RawHeaderSecret")) Assert.assertFalse(text.contains(secret), secret);
      int port = server.getAddress().getPort();
      server.stop(0);
      Assert.expectThrows(
          IllegalStateException.class,
          () ->
              new ApiClient("http://127.0.0.1:" + port).request("GET", "/orders", Map.of(), null));
      Assert.assertTrue(Files.readString(file).contains("ConnectException"));
    } finally {
      server.stop(0);
      previous.forEach(
          (key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
          });
    }
  }
}
