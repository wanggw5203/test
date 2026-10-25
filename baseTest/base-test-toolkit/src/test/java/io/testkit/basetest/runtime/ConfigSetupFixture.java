package io.testkit.basetest.runtime;

import org.testng.Assert;
import org.testng.annotations.*;

public class ConfigSetupFixture implements RuntimeProvider {
  private final AutomationRuntime runtime =
      new AutomationRuntime(
          null, null, ConfigurationReportTest.shared, null, null, null, null, null);

  public AutomationRuntime automationRuntime() {
    return runtime;
  }

  @BeforeMethod
  public void setup() {
    throw new IllegalStateException("private-secret");
  }

  @Test
  public void skippedBody() {
    Assert.fail("Should not execute");
  }
}
