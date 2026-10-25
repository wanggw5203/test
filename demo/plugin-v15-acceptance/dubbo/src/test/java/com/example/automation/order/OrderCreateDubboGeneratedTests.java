package com.example.automation.order;

import io.testkit.basetest.BaseTest;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.data.DataDriveUtil;
import io.testkit.basetest.assertion.JsonDiff;
import io.testkit.basetest.enterprise.TestCaseMetadata;
import io.testkit.basetest.runtime.PortableTestNgListener;
import java.lang.reflect.Method;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.*;

/** Framework skeleton for order_create_dubbo; source cases: TC-ORDER-DUBBO-001 */
@Listeners(PortableTestNgListener.class)
@TestCaseMetadata(id = "order_create_dubbo")
public class OrderCreateDubboGeneratedTests extends BaseTest {
    // Runtime, identity, environment and reporting are installed by the existing project's bootstrap.
    private final OrderCreateDubboGeneratedTestsDataBuilder dataBuilder = new OrderCreateDubboGeneratedTestsDataBuilder();
    private OrderCreateDubboGeneratedTestsApi api;

    @BeforeClass(alwaysRun = true)
    public void setUp() { api = new OrderCreateDubboGeneratedTestsApi(); }

    @AfterClass(alwaysRun = true)
    public void tearDown() { if (api != null) api.close(); }

    @DataProvider(name = "frameworkCases")
    public Object[][] cases(Method method) {
        return DataDriveUtil.loadTestData(method, System.getProperty("testkit.caseSuite", "order-create-dubbo"));
    }

    @Test(dataProvider = "frameworkCases")
    public void verifyApi(CaseContext context, Map<String, Object> request, Map<String, Object> expected) {
        Assert.assertEquals(currentCase().name(), context.name());
        Map<String, Object> prepared = dataBuilder.prepare(context, request);
        deferCleanup(() -> dataBuilder.cleanup(context, prepared));
        Object actual = api.invoke(prepared);


        Object expectedBody = expected;
        var differences = JsonDiff.strict(expectedBody, actual);
        Assert.assertTrue(differences.isEmpty(), context.name() + " : " + differences);
    }
}
