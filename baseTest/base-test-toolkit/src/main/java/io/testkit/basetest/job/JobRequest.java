package io.testkit.basetest.job;

import java.util.Map;

public record JobRequest(String application, String handler, String environment,
                         String lane, Map<String, Object> parameters) {
    public JobRequest {
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }
}
