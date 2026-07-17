package io.testkit.basetest;

import io.testkit.basetest.data.CaseContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class BaseTestTest extends BaseTest {
    @Test
    public void bindsDataDrivenCaseContext() {
        CaseContext context = new CaseContext("case-a", "trace", null, List.of(), Map.of());
        bindBaseTestContext(new Object[]{context, "payload"});
        Assert.assertEquals(currentCase(), context);
    }
}
