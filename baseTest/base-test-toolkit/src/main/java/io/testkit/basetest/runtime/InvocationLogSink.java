package io.testkit.basetest.runtime;

@FunctionalInterface
public interface InvocationLogSink {
    void accept(InvocationRecord record);

    static InvocationLogSink noop() {
        return record -> { };
    }
}
