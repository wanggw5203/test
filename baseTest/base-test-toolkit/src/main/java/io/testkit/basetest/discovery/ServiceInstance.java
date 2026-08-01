package io.testkit.basetest.discovery;

import java.net.URI;
import java.util.Map;

public record ServiceInstance(String id, URI address, boolean healthy,
                              Map<String, String> metadata) {
    public ServiceInstance {
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
