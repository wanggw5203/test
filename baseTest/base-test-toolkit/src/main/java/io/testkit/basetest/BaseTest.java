package io.testkit.basetest;

import io.testkit.basetest.config.TestEnvironment;
import io.testkit.basetest.data.CaseContext;
import io.testkit.basetest.data.CaseContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/** Minimal base class without application-framework or company-platform dependencies. */
public abstract class BaseTest {
    @BeforeMethod(alwaysRun = true)
    public void bindBaseTestContext(Object[] parameters) {
        for (Object parameter : parameters) {
            if (parameter instanceof CaseContext context) {
                CaseContextHolder.set(context);
                return;
            }
        }
    }

    protected TestEnvironment environment() {
        return TestEnvironment.current();
    }

    protected CaseContext currentCase() {
        return CaseContextHolder.current()
                .orElseThrow(() -> new IllegalStateException("No data-driven case is active"));
    }

    @AfterMethod(alwaysRun = true)
    public void clearBaseTestContext() {
        CaseContextHolder.clear();
        ReturnValueContext.clear();
    }
}
