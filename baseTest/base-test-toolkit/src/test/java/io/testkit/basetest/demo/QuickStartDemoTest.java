package io.testkit.basetest.demo;

import io.testkit.basetest.BaseTest;
import io.testkit.basetest.ReturnValueContext;
import io.testkit.basetest.assertion.Difference;
import io.testkit.basetest.assertion.JsonDiff;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.data.DataDriveUtil;
import io.testkit.basetest.identity.IdentityContext;
import io.testkit.basetest.identity.InMemoryIdentityProvider;
import io.testkit.basetest.identity.UseIdentity;
import io.testkit.basetest.identity.UserIdentity;
import io.testkit.basetest.runtime.AutomationRuntime;
import io.testkit.basetest.runtime.InMemoryResultPublisher;
import io.testkit.basetest.runtime.RuntimeRegistry;
import io.testkit.basetest.runtime.TestResultRecord;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * 开箱即用的快速上手示例：一个文件演示一条测试的完整生命周期。
 *
 * <p>数据驱动加载 -> 身份租借 -> Mock 场景 -> 业务调用 -> 严格断言 -> 结果发布 -> 自动清理
 *
 * <p>运行方式：mvn test -Dtest=QuickStartDemoTest 配套数据：src/test/resources/io/testkit/basetest/demo/
 */
public class QuickStartDemoTest extends BaseTest {

  public record OrderRequest(String productId, Integer quantity, String couponCode) {}

  public record OrderResponse(Integer code, String orderId, Integer totalPrice) {}

  private static final InMemoryResultPublisher RESULTS = new InMemoryResultPublisher();

  @BeforeClass
  public void installDemoRuntime() {
    Map<String, List<UserIdentity>> accounts =
        Map.of(
            "reviewer",
                List.of(
                    new UserIdentity(
                        "reviewer-1", "u-1001", "token-r1", Map.of("role", "reviewer")),
                    new UserIdentity(
                        "reviewer-2", "u-1002", "token-r2", Map.of("role", "reviewer"))),
            "operator",
                List.of(
                    new UserIdentity(
                        "operator-1", "u-2001", "token-o1", Map.of("role", "operator"))));
    AutomationRuntime runtime =
        new AutomationRuntime(
            new InMemoryIdentityProvider(accounts), null, RESULTS, null, null, null, null, null);
    RuntimeRegistry.install(runtime);
  }

  @AfterClass(alwaysRun = true)
  public void resetDemoRuntime() {
    RuntimeRegistry.reset();
  }

  @DataProvider(name = "orderCases")
  public Object[][] orderCases(Method method) {
    // 从本类包路径加载 order.yaml；enabled=false 的用例会被自动过滤。
    return DataDriveUtil.loadTestData(method, "order");
  }

  @Test(dataProvider = "orderCases")
  @UseIdentity("reviewer")
  public void createOrder(CaseContext context, OrderRequest request, OrderResponse expected) {
    // BaseTest 已把 CaseContext 绑定到当前线程，currentCase() 可直接取用。
    Assert.assertEquals(currentCase().name(), context.name());

    // @UseIdentity 触发监听器从账号池租借身份，这里直接读取。
    UserIdentity reviewer = IdentityContext.requireCurrent();
    Assert.assertEquals(reviewer.attributes().get("role"), "reviewer");

    // 真实项目中这里发送 HTTP/RPC 请求；示例用本地模拟代替，保证离线可运行。
    OrderResponse actual = createOrder(request);

    // orderId 是动态字段，用忽略路径跳过；其余字段严格比较。
    List<Difference> differences = JsonDiff.strict(expected, actual, "$.orderId");
    Assert.assertTrue(differences.isEmpty(), "Response mismatch: " + differences);
  }

  @Test(dependsOnMethods = "createOrder")
  public void lifecycleIsManagedAndResourcesAreReleased() {
    List<TestResultRecord> records = RESULTS.results();
    Assert.assertTrue(
        records.size() >= 2, "Listener should publish one record per data-driven case");
    Assert.assertTrue(
        records.stream().allMatch(record -> record.status() == TestResultRecord.Status.PASSED));

    // 用例结束后身份已归还、上下文已清理。
    Assert.assertTrue(IdentityContext.current().isEmpty());
    Assert.assertTrue(ReturnValueContext.get("orderId").isEmpty());
  }

  private OrderResponse createOrder(OrderRequest request) {
    int total = request.quantity() * 100;
    if ("SAVE20".equals(request.couponCode())) {
      total -= 20;
    }
    String orderId = "ORD-" + UUID.randomUUID();
    // 造数阶段的中间结果可以写入 ReturnValueContext，供后续步骤读取。
    ReturnValueContext.put("orderId", orderId);
    return new OrderResponse(0, orderId, total);
  }
}
