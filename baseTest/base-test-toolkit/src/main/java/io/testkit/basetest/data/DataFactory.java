package io.testkit.basetest.data;

import java.util.Map;

@FunctionalInterface
public interface DataFactory {
    Map<String, Object> prepare(DataPreparationRequest request);

    static DataFactory unsupported() {
        return request -> {
            throw new UnsupportedOperationException("No data factory adapter is configured");
        };
    }
}
