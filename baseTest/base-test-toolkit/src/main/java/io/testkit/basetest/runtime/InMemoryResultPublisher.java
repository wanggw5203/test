package io.testkit.basetest.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryResultPublisher implements ResultPublisher {
    private final CopyOnWriteArrayList<TestResultRecord> results = new CopyOnWriteArrayList<>();
    private volatile TestRunSummary summary;

    @Override
    public void publish(TestResultRecord result) {
        results.add(result);
    }

    @Override
    public void complete(TestRunSummary summary) {
        this.summary = summary;
    }

    public List<TestResultRecord> results() { return List.copyOf(results); }
    public TestRunSummary summary() { return summary; }
}
