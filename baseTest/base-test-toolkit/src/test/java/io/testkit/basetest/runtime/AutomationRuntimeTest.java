package io.testkit.basetest.runtime;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class AutomationRuntimeTest {
    @AfterMethod
    public void reset() {
        RuntimeRegistry.reset();
    }

    @Test
    public void localRuntimeProvidesSafeOfflineDefaults() {
        AutomationRuntime runtime = AutomationRuntime.local();
        RuntimeRegistry.install(runtime);

        Assert.assertTrue(runtime.discovery().discover(null).isEmpty());
        Assert.assertTrue(runtime.coverage().collect(null).isEmpty());
        Assert.assertNotNull(runtime.mocks());
        Assert.assertNotNull(runtime.results());
    }
}
