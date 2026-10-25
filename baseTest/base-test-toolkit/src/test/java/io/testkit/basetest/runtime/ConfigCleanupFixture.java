package io.testkit.basetest.runtime;

import org.testng.annotations.*;

public class ConfigCleanupFixture implements RuntimeProvider {
  private final AutomationRuntime runtime =
      new AutomationRuntime(
          null, null, ConfigurationReportTest.shared, null, null, null, null, null);

  public AutomationRuntime automationRuntime() {
    return runtime;
  }

  @Test
  public void passingBody() {}

  @AfterMethod
  public void cleanup() {
    throw new IllegalStateException("private-secret");
  }
}
