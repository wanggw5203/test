package io.testkit.basetest.job;

import java.time.Instant;

public record JobExecution(String id, State state, Instant triggeredAt, String message) {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN }
}
