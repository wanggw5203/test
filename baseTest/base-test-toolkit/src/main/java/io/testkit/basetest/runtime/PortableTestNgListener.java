package io.testkit.basetest.runtime;

import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.identity.IdentityContext;
import io.testkit.basetest.identity.UserIdentity;
import io.testkit.basetest.identity.UseIdentity;
import io.testkit.basetest.mock.MockScenario;
import io.testkit.basetest.mock.MockScope;
import io.testkit.basetest.mock.MockSession;
import org.testng.IExecutionListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Local lifecycle listener: identity scope, mock scope and result publication. */
public final class PortableTestNgListener implements ITestListener, IExecutionListener {
    private static final String STARTED_AT = PortableTestNgListener.class.getName() + ".startedAt";
    private static final String IDENTITY = PortableTestNgListener.class.getName() + ".identity";
    private static final String IDENTITY_SCOPE = PortableTestNgListener.class.getName() + ".identityScope";
    private static final String MOCK_SCOPE = PortableTestNgListener.class.getName() + ".mockScope";
    private String runId = UUID.randomUUID().toString();
    private Instant runStarted = Instant.now();
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();

    @Override
    public void onExecutionStart() {
        runId = UUID.randomUUID().toString();
        runStarted = Instant.now();
        passed.set(0);
        failed.set(0);
        skipped.set(0);
    }

    @Override
    public void onTestStart(ITestResult result) {
        result.setAttribute(STARTED_AT, Instant.now());
        AutomationRuntime runtime = RuntimeRegistry.current();
        UseIdentity useIdentity = annotation(result.getMethod().getConstructorOrMethod().getMethod(), UseIdentity.class);
        if (useIdentity != null) {
            UserIdentity identity = runtime.identities().acquire(useIdentity.value());
            result.setAttribute(IDENTITY, identity);
            result.setAttribute(IDENTITY_SCOPE, IdentityContext.use(identity));
        }
        CaseContext context = caseContext(result.getParameters());
        if (context != null && context.mockId() != null && !context.mockId().isBlank()) {
            MockSession session = runtime.mocks().open(
                    new MockScenario(context.mockId(), MockScope.CASE, java.util.List.of()));
            result.setAttribute(MOCK_SCOPE, session);
        }
    }

    @Override public void onTestSuccess(ITestResult result) { passed.incrementAndGet(); finish(result, TestResultRecord.Status.PASSED); }
    @Override public void onTestFailure(ITestResult result) { failed.incrementAndGet(); finish(result, TestResultRecord.Status.FAILED); }
    @Override public void onTestSkipped(ITestResult result) { skipped.incrementAndGet(); finish(result, TestResultRecord.Status.SKIPPED); }

    private void finish(ITestResult result, TestResultRecord.Status status) {
        try {
            Instant startedAt = (Instant) result.getAttribute(STARTED_AT);
            CaseContext context = caseContext(result.getParameters());
            String name = context == null ? result.getMethod().getMethodName() : context.name();
            String caseId = context == null ? result.getMethod().getQualifiedName() : context.name();
            String error = result.getThrowable() == null ? null : result.getThrowable().toString();
            RuntimeRegistry.current().results().publish(new TestResultRecord(
                    runId, caseId, name, status, startedAt,
                    Math.max(0L, result.getEndMillis() - result.getStartMillis()),
                    error, Map.of("class", result.getTestClass().getName())));
        } finally {
            Object mock = result.getAttribute(MOCK_SCOPE);
            if (mock instanceof MockSession session) session.close();
            Object scope = result.getAttribute(IDENTITY_SCOPE);
            if (scope instanceof IdentityContext identityContext) identityContext.close();
            Object identity = result.getAttribute(IDENTITY);
            if (identity instanceof UserIdentity userIdentity) {
                RuntimeRegistry.current().identities().release(userIdentity);
            }
        }
    }

    @Override
    public void onExecutionFinish() {
        RuntimeRegistry.current().results().complete(new TestRunSummary(
                runId, runStarted, Instant.now(), passed.get(), failed.get(), skipped.get()));
    }

    private static CaseContext caseContext(Object[] parameters) {
        if (parameters == null) return null;
        for (Object parameter : parameters) if (parameter instanceof CaseContext context) return context;
        return null;
    }

    private static <A extends java.lang.annotation.Annotation> A annotation(Method method, Class<A> type) {
        A direct = method.getAnnotation(type);
        return direct != null ? direct : method.getDeclaringClass().getAnnotation(type);
    }
}
