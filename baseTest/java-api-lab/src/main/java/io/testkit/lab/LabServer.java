package io.testkit.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Local training application: real HTTP, opaque bearer sessions and owner/role checks. */
public final class LabServer implements AutoCloseable {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, String> PASSWORDS =
      Map.of("alice", "alice123", "bob", "bob123", "admin", "admin123");

  private record Session(String username, Instant expiresAt) {}

  public record Order(
      String orderId, String owner, String productId, int quantity, int totalPrice) {}

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Map<String, Order> orders = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(8);
  private final Clock clock;
  private final Duration ttl;

  public LabServer(int port, Clock clock, Duration ttl) throws IOException {
    if (ttl.isZero() || ttl.isNegative())
      throw new IllegalArgumentException("TTL must be positive");
    this.clock = clock;
    this.ttl = ttl;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.setExecutor(executor);
    server.createContext("/", this::handle);
  }

  public void start() {
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      route(exchange);
    } catch (ApiError error) {
      send(exchange, error.status, Map.of("code", error.code));
    } catch (Exception error) {
      // Never put request bodies, passwords or bearer tokens in error responses/logs.
      send(exchange, 500, Map.of("code", "INTERNAL_ERROR"));
    } finally {
      exchange.close();
    }
  }

  private void route(HttpExchange x) throws IOException {
    String path = x.getRequestURI().getPath();
    if (path.equals("/health")) {
      method(x, "GET");
      send(x, 200, Map.of("status", "UP"));
      return;
    }
    if (path.equals("/api/auth/login")) {
      method(x, "POST");
      JsonNode body = body(x);
      String user = body.path("username").asText("");
      if (!PASSWORDS.containsKey(user)
          || !PASSWORDS.get(user).equals(body.path("password").asText()))
        throw new ApiError(401, "INVALID_CREDENTIALS");
      sessions
          .entrySet()
          .removeIf(entry -> !clock.instant().isBefore(entry.getValue().expiresAt()));
      byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      sessions.put(token, new Session(user, clock.instant().plus(ttl)));
      send(
          x,
          200,
          Map.of("accessToken", token, "tokenType", "Bearer", "expiresIn", ttl.toSeconds()));
      return;
    }
    String token = bearer(x);
    Session session = sessions.get(token);
    if (session == null) throw new ApiError(401, "INVALID_TOKEN");
    if (!clock.instant().isBefore(session.expiresAt())) {
      sessions.remove(token);
      throw new ApiError(401, "TOKEN_EXPIRED");
    }
    String user = session.username();
    if (path.equals("/api/auth/logout")) {
      method(x, "POST");
      sessions.remove(token);
      send(x, 200, Map.of("code", "LOGGED_OUT"));
    } else if (path.equals("/api/me")) {
      method(x, "GET");
      send(x, 200, Map.of("username", user, "role", user.equals("admin") ? "ADMIN" : "USER"));
    } else if (path.equals("/api/admin/stats")) {
      method(x, "GET");
      if (!user.equals("admin")) throw new ApiError(403, "FORBIDDEN");
      send(x, 200, Map.of("orderCount", orders.size()));
    } else if (path.equals("/api/orders")) {
      method(x, "POST");
      JsonNode b = body(x);
      JsonNode q = b.path("quantity");
      if (!q.isIntegralNumber() || !q.canConvertToInt() || q.intValue() < 1 || q.intValue() > 100)
        throw new ApiError(400, "INVALID_QUANTITY");
      if (!b.path("productId").isTextual() || !b.path("productId").textValue().equals("BOOK"))
        throw new ApiError(400, "INVALID_PRODUCT");
      JsonNode coupon = b.path("couponCode");
      if (!coupon.isMissingNode()
          && !coupon.isNull()
          && (!coupon.isTextual()
              || !(coupon.textValue().isEmpty() || coupon.textValue().equals("SAVE20"))))
        throw new ApiError(400, "INVALID_COUPON");
      int price = q.intValue() * 100 - (coupon.asText("").equals("SAVE20") ? 20 : 0);
      Order order = new Order(UUID.randomUUID().toString(), user, "BOOK", q.intValue(), price);
      orders.put(order.orderId(), order);
      send(x, 201, order);
    } else if (path.startsWith("/api/orders/")) {
      String verb = x.getRequestMethod();
      if (!verb.equals("GET") && !verb.equals("DELETE"))
        throw new ApiError(405, "METHOD_NOT_ALLOWED");
      String id = path.substring("/api/orders/".length());
      Order order = orders.get(id);
      if (order == null) throw new ApiError(404, "ORDER_NOT_FOUND");
      if (!order.owner().equals(user) && !user.equals("admin"))
        throw new ApiError(403, "FORBIDDEN");
      if (verb.equals("DELETE")) {
        orders.remove(id);
        send(x, 200, Map.of("code", "DELETED"));
      } else send(x, 200, order);
    } else throw new ApiError(404, "NOT_FOUND");
  }

  private static String bearer(HttpExchange x) {
    String header = x.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank())
      throw new ApiError(401, "UNAUTHORIZED");
    return header.substring(7);
  }

  private static void method(HttpExchange x, String expected) {
    if (!x.getRequestMethod().equals(expected)) throw new ApiError(405, "METHOD_NOT_ALLOWED");
  }

  private static JsonNode body(HttpExchange x) throws IOException {
    String contentType = x.getRequestHeaders().getFirst("Content-Type");
    if (contentType == null
        || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
      throw new ApiError(415, "JSON_REQUIRED");
    byte[] bytes = x.getRequestBody().readNBytes(65537);
    if (bytes.length > 65536) throw new ApiError(413, "BODY_TOO_LARGE");
    try {
      JsonNode value =
          JSON.reader()
              .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .readTree(bytes);
      if (value == null || !value.isObject()) throw new ApiError(400, "INVALID_JSON");
      return value;
    } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
      throw new ApiError(400, "INVALID_JSON");
    }
  }

  private static void send(HttpExchange x, int status, Object value) throws IOException {
    byte[] bytes = JSON.writeValueAsBytes(value);
    x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    x.getResponseHeaders().set("Cache-Control", "no-store");
    x.sendResponseHeaders(status, bytes.length);
    x.getResponseBody().write(bytes);
  }

  private static final class ApiError extends RuntimeException {
    final int status;
    final String code;

    ApiError(int status, String code) {
      this.status = status;
      this.code = code;
    }
  }

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(System.getProperty("lab.port", "8080"));
    long seconds = Long.parseLong(System.getProperty("lab.tokenTtlSeconds", "1800"));
    LabServer app = new LabServer(port, Clock.systemUTC(), Duration.ofSeconds(seconds));
    Runtime.getRuntime().addShutdownHook(new Thread(app::close));
    app.start();
    System.out.println("Java API Lab: " + app.baseUrl() + " (local training only; Ctrl+C to stop)");
  }
}
