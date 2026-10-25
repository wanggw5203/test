package io.testkit.basetest.runtime;

import io.testkit.basetest.identity.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.testng.Assert;
import org.testng.annotations.*;

public class IsolatedRuntimeFixture {
  static final Map<String, InMemoryResultPublisher> reports = new ConcurrentHashMap<>();

  @Factory
  public Object[] instances() {
    return new Object[] {new Instance("a"), new Instance("b")};
  }

  public static class Instance implements RuntimeProvider {
    private final String alias;
    private final AutomationRuntime runtime;

    public Instance(String alias) {
      this.alias = alias;
      var report = new InMemoryResultPublisher();
      reports.put(alias, report);
      runtime =
          new AutomationRuntime(
              profile -> new UserIdentity(alias, alias, "test-token", Map.of()),
              null,
              report,
              null,
              null,
              null,
              null,
              null);
    }

    public AutomationRuntime automationRuntime() {
      return runtime;
    }

    @DataProvider(name = "rows", parallel = true)
    public Object[][] rows() {
      return new Object[][] {{1}, {2}, {3}, {4}};
    }

    @Test(dataProvider = "rows")
    @UseIdentity("account")
    public void isolated(int row) throws Exception {
      Assert.assertSame(RuntimeRegistry.current(), runtime);
      Assert.assertEquals(IdentityContext.requireCurrent().alias(), alias);
      Thread.sleep(5);
      Assert.assertSame(RuntimeRegistry.current(), runtime);
    }

    @AfterMethod
    public void clean() {
      Assert.assertTrue(IdentityContext.current().isEmpty());
    }
  }
}
