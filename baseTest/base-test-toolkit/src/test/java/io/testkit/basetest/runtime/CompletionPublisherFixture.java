package io.testkit.basetest.runtime;

import org.testng.annotations.Test;

public class CompletionPublisherFixture implements RuntimeProvider {
  private final AutomationRuntime runtime =
      new AutomationRuntime(
          null,
          null,
          new ResultPublisher() {
            @Override
            public void publish(TestResultRecord result) {}

            @Override
            public void complete(TestRunSummary summary) {
              LifecycleClosureTest.completedPublishers.incrementAndGet();
              throw new AssertionError("Expected publisher completion failure");
            }
          },
          null,
          null,
          null,
          null,
          null);

  @Override
  public AutomationRuntime automationRuntime() {
    return runtime;
  }

  @Test
  public void completesBusinessAssertion() {}
}
