package io.testkit.basetest.mock;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.assertion.JsonDiff;
import io.testkit.basetest.config.ConfigLoader;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real local HTTP stub per case. The application must explicitly point its downstream URL at the
 * stub.
 */
public final class HttpMockEngine implements MockEngine {
  private final Map<String, List<MockRule>> catalog;
  private final ThreadLocal<Deque<Session>> active = ThreadLocal.withInitial(ArrayDeque::new);

  public HttpMockEngine(Map<String, List<MockRule>> catalog) {
    Map<String, List<MockRule>> copy = new HashMap<>();
    catalog.forEach((id, rules) -> copy.put(id, List.copyOf(rules)));
    this.catalog = Map.copyOf(copy);
  }

  @Override
  public Session open(MockScenario scenario) {
    List<MockRule> rules =
        scenario.rules().isEmpty() ? catalog.get(scenario.id()) : scenario.rules();
    if (rules == null || rules.isEmpty())
      throw new IllegalArgumentException("No HTTP mock rules for scenario: " + scenario.id());
    for (var rule : rules)
      if (!rule.protocol().equals("http") || !rule.target().startsWith("/"))
        throw new IllegalArgumentException(
            "HTTP stub rules require protocol=http and a root-relative path");
    try {
      Session session = new Session(new MockScenario(scenario.id(), scenario.scope(), rules));
      active.get().push(session);
      session.server.start();
      return session;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot start local HTTP mock", e);
    }
  }

  public String currentBaseUrl() {
    Session session = active.get().peek();
    if (session == null) throw new IllegalStateException("No HTTP mock active on this thread");
    return session.baseUrl();
  }

  public final class Session implements MockSession {
    private final Thread owner = Thread.currentThread();
    private final HttpServer server;
    private final MockScenario scenario;
    private final List<AtomicInteger> counts;
    private boolean closed;

    private Session(MockScenario scenario) throws java.io.IOException {
      this.scenario = scenario;
      counts = scenario.rules().stream().map(r -> new AtomicInteger()).toList();
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            try {
              byte[] payload = exchange.getRequestBody().readNBytes(65537);
              Object response = Map.of("code", "NO_MATCHING_STUB");
              int status = payload.length > 65536 ? 413 : 404;
              if (payload.length <= 65536)
                for (int i = 0; i < scenario.rules().size(); i++) {
                  MockRule rule = scenario.rules().get(i);
                  if (!rule.target().equals(exchange.getRequestURI().getPath())
                      || !rule.request()
                          .getOrDefault("method", "GET")
                          .equals(exchange.getRequestMethod())
                      || counts.get(i).get() >= rule.times()) continue;
                  if (rule.request().containsKey("body")) {
                    com.fasterxml.jackson.databind.JsonNode body;
                    try {
                      body = ConfigLoader.jsonMapper().readTree(payload);
                    } catch (java.io.IOException e) {
                      continue;
                    }
                    if (!JsonDiff.strict(rule.request().get("body"), body).isEmpty()) continue;
                  }
                  counts.get(i).incrementAndGet();
                  response = rule.response();
                  status = 200;
                  break;
                }
              byte[] bytes = ConfigLoader.jsonMapper().writeValueAsBytes(response);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(status, bytes.length);
              exchange.getResponseBody().write(bytes);
            } finally {
              exchange.close();
            }
          });
    }

    public String baseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String id() {
      return scenario.id();
    }

    public MockScenario scenario() {
      return scenario;
    }

    public boolean active() {
      return !closed;
    }

    public void close() {
      if (closed) return;
      if (owner != Thread.currentThread() || active.get().peek() != this)
        throw new IllegalStateException("HTTP mock sessions require owner-thread LIFO close");
      closed = true;
      try {
        server.stop(0);
        for (int i = 0; i < counts.size(); i++)
          if (counts.get(i).get() != scenario.rules().get(i).times())
            throw new IllegalStateException(
                "HTTP mock expected call count not met: " + scenario.rules().get(i).target());
      } finally {
        active.get().pop();
        if (active.get().isEmpty()) active.remove();
      }
    }
  }
}
