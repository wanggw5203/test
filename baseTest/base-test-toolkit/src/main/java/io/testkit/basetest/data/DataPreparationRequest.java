package io.testkit.basetest.data;

import java.util.Map;

public record DataPreparationRequest(String name, Map<String, Object> inputs) {
    public DataPreparationRequest {
        inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
    }
}
