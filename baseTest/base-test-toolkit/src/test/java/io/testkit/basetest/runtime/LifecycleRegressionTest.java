package io.testkit.basetest.runtime;

import io.testkit.basetest.identity.*;
import io.testkit.basetest.mock.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.*;
import org.testng.annotations.*;

public class LifecycleRegressionTest {
  static int mode;
  static final AtomicInteger acquired = new AtomicInteger(),
      released = new AtomicInteger(),
      bodies = new AtomicInteger();
  static final List<TestResultRecord> records = new CopyOnWriteArrayList<>();
  static final List<TestRunSummary> summaries = new CopyOnWriteArrayList<>();

  @DataProvider
  public Object[][] modes() {
    return new Object[][] {{0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}};
  }

  @Test(dataProvider = "modes")
  public void failuresDoNotLeakAndSummaryUsesCapturedPublisher(int scenario) {
    AutomationRuntime previous = RuntimeRegistry.current();
    mode = scenario;
    acquired.set(0);
    released.set(0);
    bodies.set(0);
    records.clear();
    summaries.clear();
    TestNG runner = new TestNG();
    runner.setUseDefaultListeners(false);
    runner.setVerbose(0);
    runner.setTestClasses(new Class<?>[] {LifecycleFixture.class});
    try {
      runner.run();
      Assert.assertEquals(released.get(), acquired.get());
      Assert.assertEquals(bodies.get(), mode == 1 || mode == 6 ? 0 : 2);
      Assert.assertEquals(records.size(), 2);
      Assert.assertEquals(records.stream().map(TestResultRecord::caseId).distinct().count(), 2L);
      Assert.assertEquals(summaries.size(), 1);
      Assert.assertEquals(summaries.get(0).failed(), mode == 0 || mode == 7 ? 0 : 2);
      Assert.assertEquals(summaries.get(0).skipped(), mode == 7 ? 2 : 0);
      Assert.assertEquals(runner.hasFailure(), mode != 0 && mode != 7);
      if (mode == 4) Assert.assertTrue(records.get(0).error().contains("original assertion"));
    } finally {
      RuntimeRegistry.install(previous);
    }
  }
}
