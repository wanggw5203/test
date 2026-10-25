package io.testkit.basetest.runtime;

import org.testng.*;
import org.testng.annotations.Test;

public class RuntimeIsolationTest {
  @Test
  public void perInstanceRuntimesDoNotRaceDuringParallelRows() {
    IsolatedRuntimeFixture.reports.clear();
    var runner = new TestNG();
    runner.setVerbose(0);
    runner.setUseDefaultListeners(false);
    runner.setTestClasses(new Class<?>[] {IsolatedRuntimeFixture.class});
    runner.run();
    Assert.assertFalse(runner.hasFailure());
    Assert.assertEquals(IsolatedRuntimeFixture.reports.size(), 2);
    for (var report : IsolatedRuntimeFixture.reports.values()) {
      Assert.assertEquals(report.results().size(), 4);
      Assert.assertEquals(report.summary().passed(), 4);
    }
  }
}
