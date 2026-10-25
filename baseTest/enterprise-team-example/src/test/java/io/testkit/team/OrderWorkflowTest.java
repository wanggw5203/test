package io.testkit.team;

import io.testkit.basetest.BaseTest;
import io.testkit.basetest.enterprise.CaseLevel;
import io.testkit.basetest.enterprise.EnterpriseContext;
import io.testkit.basetest.enterprise.EnterpriseExecution;
import io.testkit.basetest.enterprise.EnterpriseRuntimeProvider;
import io.testkit.basetest.enterprise.TestCaseMetadata;
import io.testkit.basetest.job.JobAwait;
import io.testkit.basetest.job.JobRequest;
import io.testkit.basetest.job.LocalJobService;
import io.testkit.basetest.message.InMemoryMessageProbe;
import io.testkit.basetest.message.TestMessage;
import io.testkit.basetest.runtime.AutomationRuntime;
import io.testkit.basetest.runtime.JsonLinesResultPublisher;
import io.testkit.basetest.runtime.TraceContext;
import io.testkit.basetest.sdk.SdkInvoker;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Local order workflow checks demonstrating enterprise ownership and invocation isolation. SDK,
 * message and job implementations are offline fixtures rather than company services.
 */
public final class OrderWorkflowTest extends BaseTest implements EnterpriseRuntimeProvider {
  private final JsonLinesResultPublisher report =
      new JsonLinesResultPublisher(
          Path.of(System.getProperty("testkit.report.file", "target/team-results.jsonl")));
  private final AutomationRuntime runtime =
      new AutomationRuntime(null, null, report, null, report, null, null, null);
  private final EnterpriseExecution execution = localExecution();

  private static EnterpriseExecution localExecution() {
    EnterpriseExecution execution = EnterpriseExecution.fromSystemProperties();
    if (!"local".equals(execution.run().environment()))
      throw new IllegalArgumentException(
          "This offline team example requires the local environment");
    return execution;
  }

  @Override
  public EnterpriseExecution enterpriseExecution() {
    return execution;
  }

  @Override
  public AutomationRuntime automationRuntime() {
    return runtime;
  }

  // Keep row IDs stable. Current framework sharding assigns the whole test method, including all
  // rows.
  @DataProvider(name = "orderQuantityScenarios", parallel = true)
  public Object[][] orderQuantityScenarios() {
    return new Object[][] {
      {"one-item", 1}, {"two-items", 2}, {"three-items", 3}, {"four-items", 4}
    };
  }

  @Test(dataProvider = "orderQuantityScenarios")
  @TestCaseMetadata(
      id = "orders.sdk-price",
      owner = "orders-qa",
      team = "orders",
      service = "order-api",
      level = CaseLevel.SMOKE,
      destructive = false)
  public void shouldCalculateOrderTotal(String rowId, int quantity) {
    String orderKey = EnterpriseContext.current().orElseThrow().fixtureKey("order-" + rowId);
    try (var sdk = new SdkInvoker(1, 4)) {
      BigDecimal total =
          sdk.call(
              "local-order.price",
              Duration.ofSeconds(2),
              () -> {
                Assert.assertEquals(
                    EnterpriseContext.current().orElseThrow().fixtureKey("order-" + rowId),
                    orderKey);
                return new BigDecimal("12.50").multiply(BigDecimal.valueOf(quantity));
              });
      Assert.assertEquals(total, new BigDecimal("12.50").multiply(BigDecimal.valueOf(quantity)));
    }
  }

  @Test(dataProvider = "orderQuantityScenarios")
  @TestCaseMetadata(
      id = "orders.sdk-context",
      owner = "orders-qa",
      team = "orders",
      service = "order-api",
      level = CaseLevel.SMOKE,
      destructive = false)
  public void shouldPropagateInvocationContextToSdkWorker(String rowId, int quantity) {
    String fixture = EnterpriseContext.current().orElseThrow().fixtureKey("order-" + rowId);
    var trace = TraceContext.current().orElseThrow();
    try (var sdk = new SdkInvoker(1, 4)) {
      String workerFixture =
          sdk.call(
              "local-order.context",
              Duration.ofSeconds(2),
              () -> {
                Assert.assertEquals(TraceContext.current().orElseThrow(), trace);
                return EnterpriseContext.current().orElseThrow().fixtureKey("order-" + rowId);
              });
      Assert.assertEquals(workerFixture, fixture);
      Assert.assertTrue(quantity > 0);
    }
  }

  @Test
  @TestCaseMetadata(
      id = "orders.local-job",
      owner = "orders-qa",
      team = "orders",
      service = "order-api",
      level = CaseLevel.REGRESSION,
      destructive = false)
  public void shouldCompleteOrderSettlementJob() {
    String fixture = EnterpriseContext.current().orElseThrow().fixtureKey("order-job");
    AtomicReference<String> completed = new AtomicReference<>();
    try (var jobs =
        new LocalJobService(
            "local",
            Map.of(
                "settle", request -> completed.set((String) request.parameters().get("fixture"))),
            1,
            4)) {
      var queued =
          jobs.trigger(
              new JobRequest("local-order", "settle", "local", null, Map.of("fixture", fixture)));
      JobAwait.succeeded(jobs, queued.id(), Duration.ofSeconds(2), Duration.ofMillis(10));
      Assert.assertEquals(completed.get(), fixture);
    }
  }

  @Test
  @TestCaseMetadata(
      id = "orders.local-message",
      owner = "orders-qa",
      team = "orders",
      service = "order-api",
      level = CaseLevel.REGRESSION,
      destructive = false)
  public void shouldConsumeMessageForCurrentOrder() {
    String fixture = EnterpriseContext.current().orElseThrow().fixtureKey("order-message");
    try (var probe = new InMemoryMessageProbe(4);
        var jobs =
            new LocalJobService(
                "local",
                Map.of(
                    "publish",
                    request ->
                        probe.publish(
                            new TestMessage(
                                "local-orders",
                                (String) request.parameters().get("fixture"),
                                "settled",
                                Map.of("fixture", (String) request.parameters().get("fixture"))))),
                1,
                4)) {
      var queued =
          jobs.trigger(
              new JobRequest("local-order", "publish", "local", null, Map.of("fixture", fixture)));
      var observed = probe.await(message -> fixture.equals(message.key()), Duration.ofSeconds(2));
      JobAwait.succeeded(jobs, queued.id(), Duration.ofSeconds(2), Duration.ofMillis(10));
      Assert.assertEquals(observed.body(), "settled");
      Assert.assertEquals(observed.headers().get("fixture"), fixture);
    }
  }
}
