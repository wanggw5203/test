package com.example.automation.smoke;
import io.testkit.basetest.BaseTest;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.data.DataDriveUtil;
import com.example.automation.config.ProjectSettings;
import java.lang.reflect.Method;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
/** Checks bootstrap + YAML/JSON wiring, not real business API availability. */
public class InitializationSmokeTest extends BaseTest {
    @DataProvider(name = "smoke") public Object[][] cases(Method method) {
        return DataDriveUtil.loadTestData(method, "initialization-smoke");
    }
    @Test(dataProvider = "smoke") public void verifiesInitialization(CaseContext context, Map<String,Object> request, Map<String,Object> expected) {
        Assert.assertEquals(currentCase().name(), context.name());
        Assert.assertEquals(request.get("message"), expected.get("message"));
        Assert.assertEquals(ProjectSettings.value("project.type"), "SDK");
    }
}
