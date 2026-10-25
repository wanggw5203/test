package io.testkit.basetest.sdk;

import io.testkit.basetest.runtime.AutomationRuntime;
import io.testkit.basetest.runtime.RuntimeRegistry;
import java.time.Duration;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SdkFailureReportingTest {
  @Test
  public void invocationFailureRemainsPrimaryWhenLoggingThrowsAnAssertionError() {
    AutomationRuntime runtime =
        new AutomationRuntime(
            null,
            null,
            null,
            null,
            record -> {
              throw new AssertionError("logging failed");
            },
            null,
            null,
            null);
    IllegalArgumentException businessFailure = new IllegalArgumentException("business call failed");
    try (SdkInvoker sdk = new SdkInvoker(1, 1);
        var scope = RuntimeRegistry.use(runtime)) {
      IllegalStateException failure =
          Assert.expectThrows(
              IllegalStateException.class,
              () ->
                  sdk.call(
                      "failing-operation",
                      Duration.ofSeconds(2),
                      () -> {
                        throw businessFailure;
                      }));
      Assert.assertSame(failure.getCause(), businessFailure);
      Assert.assertEquals(failure.getSuppressed().length, 1);
      Assert.assertTrue(failure.getSuppressed()[0] instanceof AssertionError);
    }
  }
}
