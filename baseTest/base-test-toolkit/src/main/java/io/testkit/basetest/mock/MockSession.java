package io.testkit.basetest.mock;

public interface MockSession extends AutoCloseable {
    String id();
    MockScenario scenario();
    boolean active();

    @Override
    void close();
}
