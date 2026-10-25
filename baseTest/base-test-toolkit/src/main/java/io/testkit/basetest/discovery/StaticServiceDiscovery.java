package io.testkit.basetest.discovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Metadata keys: service, environment, lane, zone. Null/blank query fields are wildcards. */
public final class StaticServiceDiscovery implements ServiceDiscovery {
  private final List<ServiceInstance> instances;

  public StaticServiceDiscovery(List<ServiceInstance> instances) {
    this.instances = List.copyOf(instances);
  }

  @Override
  public List<ServiceInstance> discover(ServiceQuery query) {
    Objects.requireNonNull(query, "query");
    return instances.stream()
        .filter(ServiceInstance::healthy)
        .filter(
            i ->
                matches(i.metadata(), "service", query.service())
                    && matches(i.metadata(), "environment", query.environment())
                    && matches(i.metadata(), "lane", query.lane())
                    && matches(i.metadata(), "zone", query.zone()))
        .toList();
  }

  private static boolean matches(Map<String, String> metadata, String key, String value) {
    return value == null || value.isBlank() || value.equals(metadata.get(key));
  }
}
