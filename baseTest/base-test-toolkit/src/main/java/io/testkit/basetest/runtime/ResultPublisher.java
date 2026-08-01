package io.testkit.basetest.runtime;

public interface ResultPublisher {
    void publish(TestResultRecord result);

    default void complete(TestRunSummary summary) {
    }
}
