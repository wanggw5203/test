package io.testkit.basetest.runtime;

import java.time.Instant;
import java.util.Map;

public record InvocationRecord(Instant time, String protocol, String target,
                               Map<String, Object> request, Object response,
                               long durationMillis, String error) {
    public InvocationRecord {
        request = Map.copyOf(request == null ? Map.of() : request);
    }
}
