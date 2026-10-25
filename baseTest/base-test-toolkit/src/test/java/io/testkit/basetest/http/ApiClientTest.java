package io.testkit.basetest.http;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.identity.*;
import io.testkit.basetest.runtime.InvocationRecord;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiClientTest {
  @Test
  public void slowBodyHasDeadlineAndOversizedBodyIsRejected() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    server.setExecutor(executor);
    server.createContext(
        "/slow",
        x -> {
          try {
            x.sendResponseHeaders(200, 1);
            Thread.sleep(1000);
            x.getResponseBody().write(1);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (java.io.IOException ignored) {
            /* Cancellation can close the peer socket. */
          } finally {
            x.close();
          }
        });
    server.createContext(
        "/large",
        x -> {
          try {
            byte[] body = new byte[8 * 1024 * 1024 + 1];
            x.sendResponseHeaders(200, body.length);
            x.getResponseBody().write(body);
          } catch (java.io.IOException ignored) {
            /* The bounded subscriber cancels the transfer. */
          } finally {
            x.close();
          }
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    try {
      long start = System.nanoTime();
      Assert.expectThrows(
          IllegalStateException.class,
          () ->
              new ApiClient(base, Duration.ofMillis(100), r -> {})
                  .request("GET", "/slow", Map.of(), null));
      Assert.assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 800);
      Assert.expectThrows(
          IllegalStateException.class,
          () ->
              new ApiClient(base, Duration.ofSeconds(3), r -> {})
                  .request("GET", "/large", Map.of(), null));
    } finally {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  @Test
  public void timeoutPreservesTransportFailureWhenLoggingAlsoFails() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/",
        x -> {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            x.close();
          }
        });
    server.start();
    try {
      var client =
          new ApiClient(
              "http://127.0.0.1:" + server.getAddress().getPort(),
              Duration.ofMillis(100),
              record -> {
                throw new IllegalStateException("log sink failed");
              });
      var failure =
          Assert.expectThrows(
              IllegalStateException.class, () -> client.request("GET", "/", Map.of(), null));
      Assert.assertTrue(failure.getMessage().contains("HTTP transport"));
      Assert.assertEquals(failure.getSuppressed().length, 1);
    } finally {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  @Test
  public void realHttpAuthBodiesRedirectsAndSafeLogs() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger hits = new AtomicInteger();
    server.createContext(
        "/",
        x -> {
          hits.incrementAndGet();
          byte[] body;
          int status;
          if (x.getRequestURI().getPath().equals("/redirect")) {
            x.getResponseHeaders().add("Location", "/ok");
            status = 302;
            body = new byte[0];
          } else {
            status =
                "Bearer private-token".equals(x.getRequestHeaders().getFirst("Authorization"))
                    ? 200
                    : 401;
            body = "{\"value\":7}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          }
          x.sendResponseHeaders(status, body.length);
          x.getResponseBody().write(body);
          x.close();
        });
    server.start();
    try {
      List<InvocationRecord> logs = new ArrayList<>();
      var client =
          new ApiClient(
              "http://127.0.0.1:" + server.getAddress().getPort(),
              Duration.ofSeconds(2),
              logs::add);
      Assert.expectThrows(
          IllegalStateException.class, () -> client.authenticated("GET", "/", null));
      client.request("GET", "/", Map.of(), null).assertStatus(401);
      try (var scope = IdentityContext.use(new UserIdentity("a", "a", "private-token", Map.of()))) {
        Assert.assertEquals(
            client
                .authenticated("POST", "/ok?password=hidden", Map.of("password", "hidden"))
                .assertStatus(200)
                .json()
                .get("value")
                .intValue(),
            7);
      }
      client.request("GET", "/redirect", Map.of(), null).assertStatus(302);
      Assert.assertEquals(hits.get(), 3);
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> client.request("GET", "http://example.com", Map.of(), null));
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> client.request("GET", "//example.com", Map.of(), null));
      Assert.assertFalse(logs.toString().contains("private-token"));
      Assert.assertFalse(logs.toString().contains("hidden"));
    } finally {
      server.stop(0);
    }
  }
}
