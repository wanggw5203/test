package io.testkit.basetest.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.TestNG;
import org.testng.annotations.Test;

public class LifecycleClosureTest {
  static InMemoryResultPublisher configuredResults;
  static InMemoryResultPublisher replacementResults;
  static final List<InvocationRecord> configurationCalls = new ArrayList<>();
  static final AtomicInteger completedPublishers = new AtomicInteger();

  @Test
  public void configurationCallsUseCapturedRuntimeAndCleanupFailureKeepsItsPublisher() {
    configuredResults = new InMemoryResultPublisher();
    replacementResults = new InMemoryResultPublisher();
    configurationCalls.clear();
    AutomationRuntime outerRuntime = RuntimeRegistry.current();
    var outerTrace = TraceContext.current();
    TestNG runner = new TestNG();
    runner.setUseDefaultListeners(false);
    runner.setVerbose(0);
    runner.setTestClasses(new Class<?>[] {ConfigurationRuntimeFixture.class});

    runner.run();

    Assert.assertTrue(runner.hasFailure());
    Assert.assertEquals(configurationCalls.size(), 1);
    Assert.assertNotNull(configurationCalls.get(0).runId());
    Assert.assertNotNull(configurationCalls.get(0).caseId());
    Assert.assertEquals(configuredResults.results().size(), 2);
    Assert.assertEquals(configuredResults.summary().passed(), 1);
    Assert.assertEquals(configuredResults.summary().failed(), 1);
    Assert.assertEquals(
        configuredResults.results().stream()
            .filter(result -> "configuration".equals(result.metadata().get("phase")))
            .filter(result -> result.status() == TestResultRecord.Status.FAILED)
            .count(),
        1L);
    Assert.assertTrue(replacementResults.results().isEmpty());
    Assert.assertNull(replacementResults.summary());
    Assert.assertSame(RuntimeRegistry.current(), outerRuntime);
    Assert.assertEquals(TraceContext.current(), outerTrace);
  }

  @Test
  public void compositePublicationAndCompletionContinueAfterAssertionErrors() {
    InMemoryResultPublisher healthy = new InMemoryResultPublisher();
    ResultPublisher failing =
        new ResultPublisher() {
          @Override
          public void publish(TestResultRecord result) {
            throw new AssertionError("publication failed");
          }

          @Override
          public void complete(TestRunSummary summary) {
            throw new AssertionError("completion failed");
          }
        };
    CompositeResultPublisher publishers = new CompositeResultPublisher(failing, healthy);
    TestResultRecord result =
        new TestResultRecord(
            "run",
            "case",
            "case",
            TestResultRecord.Status.PASSED,
            Instant.now(),
            0,
            null,
            Map.of());
    TestRunSummary summary = new TestRunSummary("run", Instant.now(), Instant.now(), 1, 0, 0);

    IllegalStateException publication =
        Assert.expectThrows(IllegalStateException.class, () -> publishers.publish(result));
    Assert.assertTrue(publication.getSuppressed()[0] instanceof AssertionError);
    Assert.assertEquals(healthy.results(), List.of(result));
    IllegalStateException completion =
        Assert.expectThrows(IllegalStateException.class, () -> publishers.complete(summary));
    Assert.assertTrue(completion.getSuppressed()[0] instanceof AssertionError);
    Assert.assertEquals(healthy.summary(), summary);
  }

  @Test
  public void listenerCompletesEveryCapturedPublisherWhenCompletionThrowsErrors() {
    completedPublishers.set(0);
    TestNG runner = new TestNG();
    runner.setUseDefaultListeners(false);
    runner.setVerbose(0);
    runner.setTestClasses(new Class<?>[] {CompletionPublisherFactoryFixture.class});

    IllegalStateException failure = Assert.expectThrows(IllegalStateException.class, runner::run);

    Assert.assertEquals(completedPublishers.get(), 2);
    Assert.assertEquals(failure.getSuppressed().length, 2);
    Assert.assertTrue(
        java.util.Arrays.stream(failure.getSuppressed())
            .allMatch(AssertionError.class::isInstance));
  }

  @Test
  public void cleanupRunsEveryCompensationInReverseOrderEvenAfterErrors() {
    List<String> released = new ArrayList<>();
    CleanupStack cleanup = new CleanupStack();
    cleanup.add(() -> released.add("oldest"));
    cleanup.add(
        () -> {
          released.add("exception");
          throw new IllegalStateException("expected");
        });
    cleanup.add(
        () -> {
          released.add("error");
          throw new LinkageError("expected");
        });

    IllegalStateException failure =
        Assert.expectThrows(IllegalStateException.class, cleanup::close);

    Assert.assertEquals(released, List.of("error", "exception", "oldest"));
    Assert.assertEquals(failure.getSuppressed().length, 2);
    cleanup.close();
    Assert.expectThrows(IllegalStateException.class, () -> cleanup.add(() -> {}));
  }
}
