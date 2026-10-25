package io.testkit.basetest.http;

import io.testkit.basetest.config.ConfigLoader;
import io.testkit.basetest.identity.IdentityContext;
import io.testkit.basetest.runtime.InvocationLogSink;
import io.testkit.basetest.runtime.InvocationRecord;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Immutable reusable HTTP client. Authentication is opt-in; redirects are never followed. */
public final class ApiClient {
  private final URI base;
  private final Duration timeout;
  private final HttpClient client;
  private final InvocationLogSink logs;

  public ApiClient(String baseUrl) {
    this(
        baseUrl,
        Duration.ofSeconds(10),
        record ->
            io.testkit.basetest.runtime.RuntimeRegistry.current().invocationLogs().accept(record));
  }

  public ApiClient(String baseUrl, Duration timeout, InvocationLogSink logs) {
    base = URI.create(baseUrl);
    if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getRawQuery() != null
        || base.getFragment() != null)
      throw new IllegalArgumentException(
          "Expected HTTP(S) base URL without credentials, query or fragment");
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Timeout must be positive");
    this.timeout = timeout;
    this.logs = Objects.requireNonNull(logs);
    client =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public ApiResponse authenticated(String method, String path, Object body) {
    return request(
        method,
        path,
        Map.of("Authorization", "Bearer " + IdentityContext.requireCurrent().accessToken()),
        body);
  }

  public ApiResponse request(String method, String path, Map<String, String> headers, Object body) {
    URI relative = URI.create(path);
    if (relative.isAbsolute()
        || relative.getRawAuthority() != null
        || relative.getFragment() != null)
      throw new IllegalArgumentException("Request path must be relative to the configured server");
    URI target = base.resolve(relative);
    if (!Objects.equals(base.getHost(), target.getHost()) || base.getPort() != target.getPort())
      throw new IllegalArgumentException("Request target must stay on the configured server");
    io.testkit.basetest.enterprise.EnterpriseContext.current()
        .ifPresent(
            context ->
                context
                    .execution()
                    .policy()
                    .targets()
                    .requireHttp(context.execution().run(), context.definition(), target));
    Instant started = Instant.now();
    long nanos = System.nanoTime();
    Integer status = null;
    String error = null;
    RuntimeException primary = null;
    Throwable diagnosticFailure = null;
    ApiResponse result = null;
    Map<String, Object> requestLog = new LinkedHashMap<>();
    requestLog.put("method", method);
    requestLog.put("headers", headers);
    requestLog.put("body", body);
    var callLog =
        io.testkit.basetest.runtime.ProtocolCallLog.start("HTTP", target.toString(), requestLog);
    try {
      var builder = HttpRequest.newBuilder(target).timeout(timeout);
      headers.forEach(builder::header);
      if (headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type")))
        builder.header("Content-Type", "application/json");
      String payload =
          body == null
              ? null
              : body instanceof String s ? s : ConfigLoader.jsonMapper().writeValueAsString(body);
      builder.method(
          method,
          payload == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(payload));
      var response = send(builder.build());
      status = response.statusCode();
      result =
          new ApiResponse(
              status,
              response.headers().map(),
              new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
      return result;
    } catch (InterruptedException e) {
      diagnosticFailure = e;
      Thread.currentThread().interrupt();
      error = e.getClass().getSimpleName();
      primary = new IllegalStateException("HTTP request interrupted");
      throw primary;
    } catch (java.io.IOException e) {
      diagnosticFailure = e;
      error = e.getClass().getSimpleName();
      primary = new IllegalStateException("HTTP transport/serialization failed: " + error);
      throw primary;
    } catch (RuntimeException e) {
      diagnosticFailure = e;
      primary = e;
      error = e.getClass().getSimpleName();
      throw e;
    } finally {
      callLog.complete(
          result == null
              ? Map.of()
              : Map.of(
                  "status", result.status(), "headers", result.headers(), "body", result.body()),
          diagnosticFailure);
      // Deliberately exclude URLs, query strings, headers and bodies: all can contain credentials.
      try {
        logs.accept(
            new InvocationRecord(
                started,
                "http",
                base.getScheme() + "://" + base.getHost() + ":" + base.getPort(),
                Map.of("method", method),
                status,
                (System.nanoTime() - nanos) / 1_000_000,
                error));
      } catch (RuntimeException logFailure) {
        if (primary != null) primary.addSuppressed(logFailure);
        else throw logFailure;
      }
    }
  }

  private HttpResponse<byte[]> send(HttpRequest request)
      throws java.io.IOException, InterruptedException {
    var future = client.sendAsync(request, info -> new LimitedBodySubscriber(8 * 1024 * 1024));
    try {
      return future.get(timeout.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      throw new java.net.http.HttpTimeoutException("HTTP response deadline exceeded");
    } catch (InterruptedException e) {
      future.cancel(true);
      throw e;
    } catch (java.util.concurrent.ExecutionException e) {
      if (e.getCause() instanceof java.io.IOException failure) throw failure;
      if (e.getCause() instanceof RuntimeException failure) throw failure;
      throw new java.io.IOException("HTTP asynchronous transport failed", e.getCause());
    }
  }

  private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    private final java.util.concurrent.CompletableFuture<byte[]> body =
        new java.util.concurrent.CompletableFuture<>();
    private java.util.concurrent.Flow.Subscription subscription;

    LimitedBodySubscriber(int limit) {
      this.limit = limit;
    }

    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return body;
    }

    public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
      subscription = value;
      value.request(Long.MAX_VALUE);
    }

    public void onNext(List<java.nio.ByteBuffer> buffers) {
      if (body.isDone()) return;
      for (var buffer : buffers) {
        if (buffer.remaining() > limit - bytes.size()) {
          subscription.cancel();
          body.completeExceptionally(new java.io.IOException("HTTP response exceeds size limit"));
          return;
        }
        byte[] part = new byte[buffer.remaining()];
        buffer.get(part);
        bytes.writeBytes(part);
      }
    }

    public void onError(Throwable failure) {
      body.completeExceptionally(failure);
    }

    public void onComplete() {
      body.complete(bytes.toByteArray());
    }
  }
}
