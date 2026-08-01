package io.testkit.basetest.discovery;

import java.util.List;

@FunctionalInterface
public interface ServiceDiscovery {
    List<ServiceInstance> discover(ServiceQuery query);

    static ServiceDiscovery empty() {
        return query -> List.of();
    }
}
