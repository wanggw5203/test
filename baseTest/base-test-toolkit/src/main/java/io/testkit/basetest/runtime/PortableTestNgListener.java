package io.testkit.basetest.runtime;

import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.enterprise.*;
import io.testkit.basetest.identity.*;
import io.testkit.basetest.mock.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import org.testng.*;

/** Captures each invocation's runtime and cleans resources independently. */
public final class PortableTestNgListener
    implements ITestListener,
        IExecutionListener,
        IInvokedMethodListener,
        IConfigurationListener,
        IMethodInterceptor {
  private static final String STATE = PortableTestNgListener.class.getName() + ".state";
  private static final String FINISHED = PortableTestNgListener.class.getName() + ".finished";
  private static final String CONFIG_RUNTIME =
      PortableTestNgListener.class.getName() + ".configRuntime";
  private String runId = UUID.randomUUID().toString();
  private Instant runStarted = Instant.now();
  private final Map<ResultPublisher, int[]> publishers =
      Collections.synchronizedMap(new IdentityHashMap<>());
  private final Map<ResultPublisher, String> publisherRuns = new IdentityHashMap<>();

  private static final class State {
    final AutomationRuntime runtime;

    State(AutomationRuntime runtime) {
      this.runtime = java.util.Objects.requireNonNull(runtime);
    }

    RuntimeRegistry.Scope runtimeScope;
    EnterpriseContext enterprise;
    EnterpriseContext.Snapshot enterpriseSnapshot;
    String recordRunId;
    final String caseId = UUID.randomUUID().toString();
    TraceContext trace;
    final Instant started = Instant.now();
    UserIdentity identity;
    IdentityContext scope;
    MockSession mock;
    boolean cleaned;
  }

  @Override
  public void onExecutionStart() {
    runId = UUID.randomUUID().toString();
    runStarted = Instant.now();
    publishers.clear();
    publisherRuns.clear();
  }

  @Override
  public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
    return EnterprisePlan.select(methods);
  }

  @Override
  public void beforeInvocation(IInvokedMethod invoked, ITestResult result) {
    if (!invoked.isTestMethod()) {
      State state = new State(runtimeFor(result));
      state.recordRunId = effectiveRunId(result);
      result.setAttribute(STATE, state);
      result.setAttribute(CONFIG_RUNTIME, state.runtime);
      try {
        state.runtimeScope = RuntimeRegistry.use(state.runtime);
        state.trace = TraceContext.use(new TraceContext.Trace(state.recordRunId, state.caseId));
      } catch (RuntimeException | Error failure) {
        fail(result, failure);
        cleanup(state, result);
        throw failure;
      }
      return;
    }
    State state = new State(runtimeFor(result));
    state.recordRunId = effectiveRunId(result);
    result.setAttribute(STATE, state);
    try {
      state.runtimeScope = RuntimeRegistry.use(state.runtime);
      state.trace = TraceContext.use(new TraceContext.Trace(state.recordRunId, state.caseId));
      Method method = result.getMethod().getConstructorOrMethod().getMethod();
      if (result.getInstance() instanceof EnterpriseRuntimeProvider provider) {
        state.enterpriseSnapshot =
            new EnterpriseContext.Snapshot(
                provider.enterpriseExecution(),
                CaseDefinition.resolve(method, result.getInstance().getClass()),
                state.caseId);
        state.enterprise = EnterpriseContext.use(state.enterpriseSnapshot);
      } else if (Boolean.getBoolean("testkit.enterprise"))
        throw new IllegalArgumentException("Enterprise mode requires EnterpriseRuntimeProvider");
      UseIdentity annotation = method.getAnnotation(UseIdentity.class);
      if (annotation == null)
        annotation = result.getTestClass().getRealClass().getAnnotation(UseIdentity.class);
      CaseContext context = caseContext(result.getParameters());
      String profile = annotation == null ? null : annotation.value();
      if (profile == null && context != null) {
        if (context.users().size() > 1)
          throw new IllegalArgumentException("Multiple YAML users require explicit @UseIdentity");
        if (!context.users().isEmpty()) profile = context.users().get(0);
      }
      if (profile != null) {
        state.identity = state.runtime.identities().acquire(profile);
        state.scope = IdentityContext.use(state.identity);
      }
      if (context != null && context.mockId() != null && !context.mockId().isBlank())
        state.mock =
            state
                .runtime
                .mocks()
                .open(new MockScenario(context.mockId(), MockScope.CASE, List.of()));
    } catch (RuntimeException | Error failure) {
      fail(result, failure);
      cleanup(state, result);
      throw failure;
    }
  }

  @Override
  public void afterInvocation(IInvokedMethod invoked, ITestResult result) {
    if (result.getAttribute(STATE) instanceof State state) cleanup(state, result);
  }

  private static void cleanup(State state, ITestResult result) {
    if (state.cleaned) return;
    state.cleaned = true;
    attempt(
        result,
        () -> {
          if (state.mock != null) state.mock.close();
        });
    attempt(
        result,
        () -> {
          if (state.scope != null) state.scope.close();
        });
    attempt(
        result,
        () -> {
          if (state.identity != null) state.runtime.identities().release(state.identity);
        });
    attempt(
        result,
        () -> {
          if (state.enterprise != null) state.enterprise.close();
        });
    attempt(
        result,
        () -> {
          if (state.runtimeScope != null) state.runtimeScope.close();
        });
    attempt(
        result,
        () -> {
          if (state.trace != null) state.trace.close();
        });
  }

  private static void attempt(ITestResult result, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | Error failure) {
      fail(result, failure);
    }
  }

  private static void fail(ITestResult result, Throwable failure) {
    if (result.getThrowable() == null) result.setThrowable(failure);
    else if (result.getThrowable() != failure) result.getThrowable().addSuppressed(failure);
    result.setStatus(ITestResult.FAILURE);
  }

  @Override
  public void onTestSuccess(ITestResult result) {
    finish(result);
  }

  @Override
  public void onTestFailure(ITestResult result) {
    finish(result);
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    finish(result);
  }

  @Override
  public void onTestFailedWithTimeout(ITestResult result) {
    finish(result);
  }

  @Override
  public void onConfigurationFailure(ITestResult result) {
    finish(result);
  }

  private static AutomationRuntime runtimeFor(ITestResult result) {
    if (result.getAttribute(STATE) instanceof State captured) return captured.runtime;
    if (result.getAttribute(CONFIG_RUNTIME) instanceof AutomationRuntime captured) return captured;
    if (result.getInstance() instanceof RuntimeProvider provider) {
      AutomationRuntime runtime = provider.automationRuntime();
      if (runtime != null) return runtime;
    }
    return RuntimeRegistry.current();
  }

  private String effectiveRunId(ITestResult result) {
    return result.getInstance() instanceof EnterpriseRuntimeProvider provider
        ? provider.enterpriseExecution().run().runId()
        : runId;
  }

  private void finish(ITestResult result) {
    synchronized (result) {
      if (Boolean.TRUE.equals(result.getAttribute(FINISHED))) return;
      result.setAttribute(FINISHED, true);
    }
    boolean configuration = !result.getMethod().isTest();
    State state = result.getAttribute(STATE) instanceof State s ? s : new State(runtimeFor(result));
    if (state.recordRunId == null) state.recordRunId = effectiveRunId(result);
    CaseContext context = caseContext(result.getParameters());
    String name = context == null ? result.getMethod().getMethodName() : context.name();
    String caseId = state.caseId;
    TestResultRecord.Status status =
        switch (result.getStatus()) {
          case ITestResult.SUCCESS -> TestResultRecord.Status.PASSED;
          case ITestResult.SKIP -> TestResultRecord.Status.SKIPPED;
          default -> TestResultRecord.Status.FAILED;
        };
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("class", result.getTestClass().getName());
    metadata.put("phase", configuration ? "configuration" : "test");
    if (result.getInstance() instanceof EnterpriseRuntimeProvider provider) {
      var execution = provider.enterpriseExecution();
      var run = execution.run();
      metadata.put("team", run.team());
      metadata.put("environment", run.environment());
      metadata.put("workerId", run.workerId());
      metadata.put("shardIndex", execution.shard().index());
      metadata.put("shardCount", execution.shard().count());
    }
    if (state.enterpriseSnapshot != null) {
      var definition = state.enterpriseSnapshot.definition();
      var run = state.enterpriseSnapshot.execution().run();
      metadata.put("logicalCaseId", definition.id());
      metadata.put("owner", definition.owner());
      metadata.put("team", definition.team());
      metadata.put("service", definition.service());
      metadata.put("level", definition.level().name());
      metadata.put("environment", run.environment());
      metadata.put("workerId", run.workerId());
    }
    try {
      synchronized (publishers) {
        String previous = publisherRuns.putIfAbsent(state.runtime.results(), state.recordRunId);
        if (previous != null && !previous.equals(state.recordRunId))
          throw new IllegalArgumentException("A publisher cannot mix execution run IDs");
      }
      state
          .runtime
          .results()
          .publish(
              new TestResultRecord(
                  state.recordRunId,
                  caseId,
                  name,
                  status,
                  state.started,
                  Math.max(0, result.getEndMillis() - result.getStartMillis()),
                  result.getThrowable() == null ? null : result.getThrowable().toString(),
                  metadata));
    } catch (RuntimeException | Error failure) {
      fail(result, failure);
      status = TestResultRecord.Status.FAILED;
    }
    synchronized (publishers) {
      int[] counts = publishers.computeIfAbsent(state.runtime.results(), key -> new int[3]);
      counts[
          status == TestResultRecord.Status.PASSED
              ? 0
              : status == TestResultRecord.Status.FAILED ? 1 : 2]++;
    }
  }

  @Override
  public void onExecutionFinish() {
    RuntimeException combined =
        new IllegalStateException("One or more result publishers failed to complete");
    synchronized (publishers) {
      publishers.forEach(
          (publisher, counts) -> {
            try {
              publisher.complete(
                  new TestRunSummary(
                      publisherRuns.getOrDefault(publisher, runId),
                      runStarted,
                      Instant.now(),
                      counts[0],
                      counts[1],
                      counts[2]));
            } catch (RuntimeException | Error failure) {
              combined.addSuppressed(failure);
            }
          });
    }
    if (combined.getSuppressed().length > 0) throw combined;
  }

  private static CaseContext caseContext(Object[] parameters) {
    if (parameters != null) for (Object p : parameters) if (p instanceof CaseContext c) return c;
    return null;
  }
}
