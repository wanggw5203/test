package io.testkit.basetest.runtime;

import static io.testkit.basetest.runtime.LifecycleClosureTest.configurationCalls;
import static io.testkit.basetest.runtime.LifecycleClosureTest.configuredResults;
import static io.testkit.basetest.runtime.LifecycleClosureTest.replacementResults;

import io.testkit.basetest.sdk.SdkInvoker;
import java.time.Duration;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConfigurationRuntimeFixture implements RuntimeProvider {
  private AutomationRuntime runtime =
      new AutomationRuntime(
          null, null, configuredResults, null, configurationCalls::add, null, null, null);

  @Override
  public AutomationRuntime automationRuntime() {
    return runtime;
  }

  @BeforeMethod
  public void prepareDataUsingConfiguredRuntime() {
    Assert.assertSame(RuntimeRegistry.current(), runtime);
    Assert.assertTrue(TraceContext.current().isPresent());
    try (SdkInvoker sdk = new SdkInvoker(1, 1)) {
      Assert.assertSame(
          sdk.call("prepare-data", Duration.ofSeconds(2), RuntimeRegistry::current), runtime);
    }
  }

  @Test
  public void executesBusinessAssertion() {
    Assert.assertSame(RuntimeRegistry.current(), runtime);
  }

  @AfterMethod
  public void teardownChangesProviderAndFails() {
    runtime = new AutomationRuntime(null, null, replacementResults, null, null, null, null, null);
    throw new IllegalStateException("Expected fixture cleanup failure");
  }
}
