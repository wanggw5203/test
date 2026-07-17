package io.testkit.basetest.config;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class TestEnvironmentTest {
    @AfterMethod
    public void clear() {
        System.clearProperty("test.env");
        System.clearProperty("test.attr.region");
    }

    @Test
    public void systemPropertiesOverrideDefaults() {
        System.setProperty("test.env", "integration");
        System.setProperty("test.attr.region", "east");
        TestEnvironment environment = TestEnvironment.current();
        Assert.assertEquals(environment.name(), "integration");
        Assert.assertEquals(environment.attribute("region").orElseThrow(), "east");
    }
}
