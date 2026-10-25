package io.testkit.basetest.enterprise;

import io.testkit.basetest.identity.IdentityContext;
import io.testkit.basetest.identity.UserIdentity;
import io.testkit.basetest.runtime.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.testng.*;
import org.testng.annotations.Test;
import org.testng.internal.ConstructorOrMethod;

public class EnterpriseModeTest {
  private static EnterpriseExecution execution(String env, Shard shard) {
    return new EnterpriseExecution(
        new RunMetadata("run", "worker", env, "orders"), ExecutionPolicy.localSafe(), shard);
  }

  @CaseInfo(
      id = "orders.one",
      owner = "owner",
      team = "orders",
      service = "orders-api",
      level = CaseLevel.SMOKE)
  public void one() {}

  @CaseInfo(id = "orders.two", owner = "owner", team = "orders", service = "orders-api")
  public void two() {}

  @CaseInfo(id = "orders.one", owner = "owner", team = "orders", service = "orders-api")
  public void duplicate() {}

  @CaseInfo(
      id = "orders.delete",
      owner = "owner",
      team = "orders",
      service = "orders-api",
      destructive = true)
  public void destructive() {}

  @CaseInfo(id = "orders.empty-owner", team = "orders", service = "orders-api")
  public void missingOwner() {}

  public void missingMetadata() {}

  private static IMethodInstance method(
      String name, EnterpriseExecution execution, boolean dependency) throws Exception {
    Method reflected = EnterpriseModeTest.class.getMethod(name);
    EnterpriseRuntimeProvider provider =
        new EnterpriseRuntimeProvider() {
          public EnterpriseExecution enterpriseExecution() {
            return execution;
          }

          public AutomationRuntime automationRuntime() {
            return AutomationRuntime.local();
          }
        };
    ITestNGMethod method =
        (ITestNGMethod)
            Proxy.newProxyInstance(
                EnterpriseModeTest.class.getClassLoader(),
                new Class<?>[] {ITestNGMethod.class},
                (p, m, a) ->
                    switch (m.getName()) {
                      case "getConstructorOrMethod" -> new ConstructorOrMethod(reflected);
                      case "getMethodsDependedUpon" ->
                          dependency ? new String[] {"one"} : new String[0];
                      case "getGroupsDependedUpon" -> new String[0];
                      default -> throw new UnsupportedOperationException(m.getName());
                    });
    return (IMethodInstance)
        Proxy.newProxyInstance(
            EnterpriseModeTest.class.getClassLoader(),
            new Class<?>[] {IMethodInstance.class},
            (p, m, a) ->
                switch (m.getName()) {
                  case "getInstance" -> provider;
                  case "getMethod" -> method;
                  default -> throw new UnsupportedOperationException(m.getName());
                });
  }

  @Test
  public void stableShardsCoverEveryLogicalCaseExactlyOnce() {
    for (int count : List.of(1, 2, 4, 8, 16))
      for (int row = 0; row < 200; row++) {
        int owners = 0;
        for (int index = 0; index < count; index++)
          if (new Shard(index, count).owns("orders.case-" + row)) owners++;
        Assert.assertEquals(owners, 1);
      }
    Assert.expectThrows(IllegalArgumentException.class, () -> new Shard(2, 2));
  }

  @Test
  public void metadataPolicyAndDuplicateValidationRunBeforeSelection() throws Exception {
    var local = execution("local", new Shard(0, 1));
    Assert.assertEquals(
        EnterprisePlan.select(List.of(method("one", local, false), method("two", local, false)))
            .size(),
        2);
    for (String name : List.of("missingOwner", "missingMetadata", "destructive"))
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> EnterprisePlan.select(List.of(method(name, local, false))));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            EnterprisePlan.select(
                List.of(method("one", local, false), method("duplicate", local, false))));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            EnterprisePlan.select(
                List.of(method("one", execution("prod", new Shard(0, 1)), false))));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            EnterprisePlan.select(List.of(method("one", execution("qa", new Shard(0, 2)), true))));
    int unowned = new Shard(0, 2).owns("orders.one") ? 1 : 0;
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            EnterprisePlan.select(
                List.of(method("one", execution("qa", new Shard(unowned, 2)), false))));
    var definition =
        CaseDefinition.resolve(EnterpriseModeTest.class.getMethod("one"), EnterpriseModeTest.class);
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            new ExecutionPolicy(Set.of("local"), Set.of("other"), false)
                .validate(local.run(), definition));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () -> local.policy().validate(new RunMetadata("r", "w", "local", "other"), definition));
  }

  @Test
  public void oneHundredConcurrentWorkersNeverShareScopesOrFixtureKeys() throws Exception {
    var pool = Executors.newFixedThreadPool(100);
    var barrier = new CyclicBarrier(100);
    Set<String> keys = ConcurrentHashMap.newKeySet();
    List<Future<?>> futures = new ArrayList<>();
    var definition =
        CaseDefinition.resolve(EnterpriseModeTest.class.getMethod("one"), EnterpriseModeTest.class);
    try {
      for (int index = 0; index < 100; index++) {
        final int worker = index;
        futures.add(
            pool.submit(
                () -> {
                  var run =
                      new EnterpriseExecution(
                          new RunMetadata("pipeline", "worker-" + worker, "qa", "orders"),
                          ExecutionPolicy.localSafe(),
                          new Shard(0, 1));
                  var snapshot = new EnterpriseContext.Snapshot(run, definition, "row-" + worker);
                  var runtime = AutomationRuntime.local();
                  var identity =
                      new UserIdentity(
                          "worker-" + worker, "worker-" + worker, "test-token", Map.of());
                  var trace = new TraceContext.Trace("pipeline", "row-" + worker);
                  try (var runtimeScope = RuntimeRegistry.use(runtime);
                      var identityScope = IdentityContext.use(identity);
                      var traceScope = TraceContext.use(trace);
                      var scope = EnterpriseContext.use(snapshot)) {
                    barrier.await(10, TimeUnit.SECONDS);
                    Assert.assertSame(EnterpriseContext.current().orElseThrow(), snapshot);
                    Assert.assertSame(RuntimeRegistry.current(), runtime);
                    Assert.assertSame(IdentityContext.requireCurrent(), identity);
                    Assert.assertEquals(TraceContext.current().orElseThrow(), trace);
                    String key = snapshot.fixtureKey("order");
                    Assert.assertTrue(keys.add(key));
                    Assert.assertEquals(key, snapshot.fixtureKey("order"));
                  }
                  Assert.assertTrue(EnterpriseContext.current().isEmpty());
                  Assert.assertTrue(IdentityContext.current().isEmpty());
                  Assert.assertTrue(TraceContext.current().isEmpty());
                  return null;
                }));
      }
      for (var future : futures) future.get(15, TimeUnit.SECONDS);
      Assert.assertEquals(keys.size(), 100);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void namespaceIncludesInvocationAndScopesEnforceLifo() throws Exception {
    var definition =
        CaseDefinition.resolve(EnterpriseModeTest.class.getMethod("one"), EnterpriseModeTest.class);
    var run = execution("qa", new Shard(0, 1));
    var first = new EnterpriseContext.Snapshot(run, definition, "first");
    var second = new EnterpriseContext.Snapshot(run, definition, "second");
    Assert.assertNotEquals(first.fixtureKey("order"), second.fixtureKey("order"));
    var outer = EnterpriseContext.use(first);
    var inner = EnterpriseContext.use(second);
    try {
      Assert.expectThrows(IllegalStateException.class, outer::close);
    } finally {
      inner.close();
      outer.close();
    }
    Assert.assertTrue(EnterpriseContext.current().isEmpty());
  }

  @Test
  public void strictEntryAndLevelSelectionAreEnforced() throws Exception {
    String previousStrict = System.getProperty("testkit.enterprise"),
        previousLevel = System.getProperty("testkit.level");
    try {
      System.setProperty("testkit.level", "smoke");
      var run = execution("local", new Shard(0, 1));
      Assert.assertEquals(
          EnterprisePlan.select(List.of(method("one", run, false), method("two", run, false)))
              .size(),
          1);
      System.setProperty("testkit.level", "invalid");
      Assert.expectThrows(IllegalArgumentException.class, () -> EnterprisePlan.select(List.of()));
      System.setProperty("testkit.level", "all");
      System.setProperty("testkit.enterprise", "true");
      var ordinary =
          (IMethodInstance)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {IMethodInstance.class},
                  (p, m, a) -> new Object());
      Assert.expectThrows(
          IllegalArgumentException.class, () -> EnterprisePlan.select(List.of(ordinary)));
      Assert.expectThrows(IllegalArgumentException.class, () -> EnterprisePlan.select(List.of()));
    } finally {
      if (previousStrict == null) System.clearProperty("testkit.enterprise");
      else System.setProperty("testkit.enterprise", previousStrict);
      if (previousLevel == null) System.clearProperty("testkit.level");
      else System.setProperty("testkit.level", previousLevel);
    }
  }
}
