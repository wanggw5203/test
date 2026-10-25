package io.testkit.basetest;

import io.testkit.basetest.assertion.Await;
import io.testkit.basetest.config.EnvironmentConfig;
import io.testkit.basetest.database.JdbcSession;
import io.testkit.basetest.identity.*;
import io.testkit.basetest.job.*;
import io.testkit.basetest.message.*;
import io.testkit.basetest.runtime.*;
import io.testkit.basetest.sdk.SdkInvoker;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EnterpriseCapabilitiesTest {
  @Test
  public void environmentOverridesSecretsAndUnknownProfiles() throws Exception {
    Path file = Files.createTempFile("environments", ".yaml");
    try {
      Files.writeString(
          file,
          "common:\n  http.timeout.ms: 100\nenvironments:\n  dev:\n    http.base.url: ${DEV_URL}\n    password: ${PASSWORD}\n  qa:\n    http.base.url: https://qa.invalid\n");
      var dev =
          EnvironmentConfig.load(
              file,
              "dev",
              Map.of(
                  "DEV_URL",
                  "https://dev.invalid",
                  "PASSWORD",
                  "sensitive",
                  "TEST_ATTR_HTTP_TIMEOUT_MS",
                  "200"),
              Map.of("test.attr.http.timeout.ms", "300"));
      Assert.assertEquals(dev.positiveInt("http.timeout.ms"), 300);
      Assert.assertEquals(dev.require("http.base.url"), "https://dev.invalid");
      Assert.assertFalse(dev.toString().contains("sensitive"));
      Assert.assertEquals(
          EnvironmentConfig.load(file, "qa", Map.of(), Map.of()).require("http.base.url"),
          "https://qa.invalid");
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> EnvironmentConfig.load(file, "prod", Map.of(), Map.of()));
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> EnvironmentConfig.load(file, "dev", Map.of(), Map.of()));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void sdkPropagatesContextsTimesOutAndReusesWorkerWithoutIdentityLeak() {
    AutomationRuntime runtime = AutomationRuntime.local();
    try (var sdk = new SdkInvoker(1, 2);
        var scope = RuntimeRegistry.use(runtime);
        var trace = TraceContext.use(new TraceContext.Trace("run", "case"))) {
      try (var user = IdentityContext.use(new UserIdentity("alice", "alice", "token", Map.of()))) {
        Assert.assertEquals(
            sdk.call(
                "read",
                Duration.ofSeconds(2),
                () -> {
                  Assert.assertSame(RuntimeRegistry.current(), runtime);
                  Assert.assertEquals(TraceContext.current().orElseThrow().caseId(), "case");
                  return IdentityContext.requireCurrent().alias();
                }),
            "alice");
      }
      Assert.assertTrue(
          sdk.call("clean", Duration.ofSeconds(2), () -> IdentityContext.current().isEmpty()));
      Assert.expectThrows(
          IllegalStateException.class,
          () ->
              sdk.call(
                  "timeout",
                  Duration.ofMillis(30),
                  () -> {
                    Thread.sleep(1000);
                    return null;
                  }));
      Assert.assertEquals(sdk.call("reuse", Duration.ofSeconds(2), () -> "ok"), "ok");
      var failure =
          Assert.expectThrows(
              IllegalStateException.class,
              () ->
                  sdk.call(
                      "business",
                      Duration.ofSeconds(2),
                      () -> {
                        throw new IllegalArgumentException("business failure");
                      }));
      Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void delayedJobsHaveTerminalStateAndRejectCrossEnvironment() {
    try (var jobs =
        new LocalJobService(
            "qa",
            Map.of(
                "success",
                r -> {},
                "failure",
                r -> {
                  throw new IllegalStateException();
                }),
            1,
            10)) {
      var request = new JobRequest("orders", "success", "qa", "stable", Map.of());
      var queued = jobs.schedule(request, Duration.ofMillis(30));
      Assert.assertEquals(queued.state(), JobExecution.State.QUEUED);
      Assert.assertEquals(
          Await.until(
                  "job",
                  Duration.ofSeconds(2),
                  Duration.ofMillis(5),
                  () -> jobs.query(queued.id()).orElseThrow(),
                  result -> result.state() == JobExecution.State.SUCCEEDED)
              .state(),
          JobExecution.State.SUCCEEDED);
      var failure = jobs.trigger(new JobRequest("orders", "failure", "qa", null, Map.of()));
      Await.until(
          "failure",
          Duration.ofSeconds(2),
          Duration.ofMillis(5),
          () -> jobs.query(failure.id()).orElseThrow(),
          j -> j.state() == JobExecution.State.FAILED);
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> jobs.trigger(new JobRequest("orders", "success", "dev", null, Map.of())));
    }
  }

  @Test
  public void correlatedMessageTimeoutAndOverflow() {
    try (var probe = new InMemoryMessageProbe(2)) {
      probe.publish(new TestMessage("orders", "old", "old", Map.of()));
      probe.publish(new TestMessage("orders", "run-1", "success", Map.of()));
      Assert.expectThrows(
          RejectedExecutionException.class,
          () -> probe.publish(new TestMessage("orders", "overflow", "x", Map.of())));
      Assert.assertEquals(
          probe.await(m -> m.key().equals("run-1"), Duration.ofSeconds(1)).body(), "success");
      Assert.expectThrows(AssertionError.class, () -> probe.await(m -> true, Duration.ofMillis(5)));
    }
  }

  @Test
  public void cleanupContinuesAndUsesReverseOrder() {
    List<Integer> order = new ArrayList<>();
    CleanupStack stack = new CleanupStack();
    stack.add(() -> order.add(1));
    stack.add(
        () -> {
          order.add(2);
          throw new IllegalStateException("broken");
        });
    stack.add(() -> order.add(3));
    Assert.expectThrows(IllegalStateException.class, stack::close);
    Assert.assertEquals(order, List.of(3, 2, 1));
    stack.close();
  }

  @Test
  public void scopesAreThreadIsolatedAndEnforceLifo() throws Exception {
    var first = RuntimeRegistry.use(AutomationRuntime.local());
    var second = RuntimeRegistry.use(AutomationRuntime.local());
    try {
      Assert.expectThrows(IllegalStateException.class, first::close);
    } finally {
      second.close();
      first.close();
    }
    var user = new UserIdentity("a", "a", "token", Map.of());
    var outer = IdentityContext.use(user);
    var inner = IdentityContext.use(user);
    try {
      Assert.expectThrows(IllegalStateException.class, outer::close);
    } finally {
      inner.close();
      outer.close();
    }
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      AutomationRuntime a = AutomationRuntime.local(), b = AutomationRuntime.local();
      CyclicBarrier barrier = new CyclicBarrier(2);
      var f1 =
          pool.submit(
              () -> {
                try (var s = RuntimeRegistry.use(a)) {
                  barrier.await();
                  return RuntimeRegistry.current();
                }
              });
      var f2 =
          pool.submit(
              () -> {
                try (var s = RuntimeRegistry.use(b)) {
                  barrier.await();
                  return RuntimeRegistry.current();
                }
              });
      Assert.assertSame(f1.get(2, TimeUnit.SECONDS), a);
      Assert.assertSame(f2.get(2, TimeUnit.SECONDS), b);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void jdbcPreparedQuerySupportsNullAndRollback() throws Exception {
    String url = "jdbc:h2:mem:testkit;DB_CLOSE_DELAY=-1";
    try (var init = java.sql.DriverManager.getConnection(url);
        var statement = init.createStatement()) {
      statement.execute(
          "CREATE TABLE IF NOT EXISTS fixture (id INT PRIMARY KEY, name VARCHAR(100))");
    }
    try (var db = new JdbcSession(java.sql.DriverManager.getConnection(url), 2, 10)) {
      Assert.assertEquals(db.update("INSERT INTO fixture VALUES (?, ?)", 1, null), 1);
      Assert.assertNull(db.query("SELECT * FROM fixture WHERE id = ?", 1).get(0).get("NAME"));
    }
    try (var db = new JdbcSession(java.sql.DriverManager.getConnection(url), 2, 10)) {
      Assert.assertTrue(db.query("SELECT * FROM fixture").isEmpty());
    }
  }
}
