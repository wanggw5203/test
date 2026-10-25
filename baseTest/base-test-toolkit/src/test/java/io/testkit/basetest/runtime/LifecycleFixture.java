package io.testkit.basetest.runtime;

import static io.testkit.basetest.runtime.LifecycleRegressionTest.*;

import io.testkit.basetest.BaseTest;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.identity.*;
import io.testkit.basetest.mock.*;
import java.util.*;
import org.testng.*;
import org.testng.annotations.*;

public class LifecycleFixture extends BaseTest {
  @BeforeClass
  public void setup() {
    RuntimeRegistry.install(
        new AutomationRuntime(
            new IdentityProvider() {
              public UserIdentity acquire(String profile) {
                if (mode == 6) throw new IllegalStateException("acquire failed");
                acquired.incrementAndGet();
                return new UserIdentity(profile, profile, "secret", Map.of());
              }

              public void release(UserIdentity identity) {
                released.incrementAndGet();
                if (mode == 3 || mode == 4) throw new IllegalStateException("release failed");
              }
            },
            scenario -> {
              if (mode == 1) throw new IllegalStateException("open failed");
              return new MockSession() {
                public String id() {
                  return "mock";
                }

                public MockScenario scenario() {
                  return scenario;
                }

                public boolean active() {
                  return true;
                }

                public void close() {
                  if (mode == 2 || mode == 4) throw new IllegalStateException("close failed");
                }
              };
            },
            new ResultPublisher() {
              public void publish(TestResultRecord record) {
                records.add(record);
                if (mode == 5) throw new IllegalStateException("publish failed");
              }

              public void complete(TestRunSummary summary) {
                summaries.add(summary);
              }
            },
            null,
            null,
            null,
            null,
            null));
  }

  @DataProvider(name = "rows", parallel = true)
  public Object[][] rows() {
    return new Object[][] {
      {new CaseContext("same-name", "", "mock", List.of("alice"), Map.of())},
      {new CaseContext("same-name", "", "mock", List.of("bob"), Map.of())}
    };
  }

  @Test(dataProvider = "rows")
  public void body(CaseContext context) {
    bodies.incrementAndGet();
    Assert.assertEquals(IdentityContext.requireCurrent().alias(), context.users().get(0));
    if (mode == 7) throw new SkipException("intentional skip");
    if (mode == 4) throw new AssertionError("original assertion");
  }

  @AfterMethod(alwaysRun = true)
  public void verifyScope() {
    Assert.assertTrue(IdentityContext.current().isEmpty());
  }

  @AfterClass(alwaysRun = true)
  public void resetBeforeSummary() {
    RuntimeRegistry.reset();
  }
}
