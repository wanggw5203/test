package demo.golden.framework.v3.dubbo;

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

/** Framework skeleton for golden_order_dubbo_framework_v3; source cases: GOLD-001, GOLD-002, GOLD-003 */
@Listeners(PortableTestNgListener.class)
@TestCaseMetadata(id = "golden_order_dubbo_framework_v3")
public class GoldenOrderDubboTest extends BaseTest {
    // Runtime, identity, environment and reporting are installed by the existing project's bootstrap.
    private final GoldenOrderDubboTestDataBuilder dataBuilder = new GoldenOrderDubboTestDataBuilder();
    private GoldenOrderDubboTestApi api;

    @BeforeClass(alwaysRun = true)
    public void setUp() { api = new GoldenOrderDubboTestApi(); }

    @AfterClass(alwaysRun = true)
    public void tearDown() { if (api != null) api.close(); }

    @DataProvider(name = "frameworkCases")
    public Object[][] cases(Method method) {
        return DataDriveUtil.loadTestData(method, System.getProperty("testkit.caseSuite", "golden_order_dubbo_framework_v3"));
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
