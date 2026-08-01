package io.testkit.basetest.mock;

import java.util.Map;

public record MockRule(String protocol, String target, Map<String, Object> request,
                       Object response, int times) {
    public MockRule {
        if (protocol == null || protocol.isBlank()) throw new IllegalArgumentException("protocol must not be blank");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target must not be blank");
        request = Map.copyOf(request == null ? Map.of() : request);
        if (times <= 0) times = 1;
    }
}
