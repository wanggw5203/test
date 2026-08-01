package io.testkit.basetest.runtime;

import io.testkit.basetest.coverage.CoverageCollector;
import io.testkit.basetest.data.DataFactory;
import io.testkit.basetest.discovery.ServiceDiscovery;
import io.testkit.basetest.identity.IdentityProvider;
import io.testkit.basetest.job.JobService;
import io.testkit.basetest.mock.InMemoryMockEngine;
import io.testkit.basetest.mock.MockEngine;

/** Explicit composition root replacing hidden static clients and organization-specific auto-configuration. */
public record AutomationRuntime(
        IdentityProvider identities,
        MockEngine mocks,
        ResultPublisher results,
        CoverageCollector coverage,
        InvocationLogSink invocationLogs,
        JobService jobs,
        ServiceDiscovery discovery,
        DataFactory dataFactory) {

    public AutomationRuntime {
        if (identities == null) identities = profile -> {
            throw new UnsupportedOperationException("No identity provider is configured");
        };
        if (mocks == null) mocks = new InMemoryMockEngine();
        if (results == null) results = new InMemoryResultPublisher();
        if (coverage == null) coverage = CoverageCollector.noop();
        if (invocationLogs == null) invocationLogs = InvocationLogSink.noop();
        if (jobs == null) jobs = JobService.unsupported();
        if (discovery == null) discovery = ServiceDiscovery.empty();
        if (dataFactory == null) dataFactory = DataFactory.unsupported();
    }

    public static AutomationRuntime local() {
        return new AutomationRuntime(null, null, null, null, null, null, null, null);
    }
}
