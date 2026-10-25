package io.github.aitestflow.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe registry that composes manual, PSI and external catalog discovery providers. */
public final class InterfaceDiscoveryRegistry {
    private final CopyOnWriteArrayList<InterfaceDiscoveryProvider> providers = new CopyOnWriteArrayList<>();

    public InterfaceDiscoveryRegistry() {
    }

    public InterfaceDiscoveryRegistry(Collection<? extends InterfaceDiscoveryProvider> initialProviders) {
        if (initialProviders != null) {
            initialProviders.forEach(this::register);
        }
    }

    public void register(InterfaceDiscoveryProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        providers.removeIf(existing -> existing.id().equals(provider.id()));
        providers.add(provider);
    }

    public boolean unregister(String providerId) {
        return providerId != null && providers.removeIf(provider -> provider.id().equals(providerId));
    }

    public List<InterfaceDiscoveryProvider> providers() {
        return providers.stream().sorted(providerOrder()).toList();
    }

    /**
     * Queries every supporting provider. If providers return the same endpoint id, the endpoint
     * from the provider with the higher priority wins.
     */
    public List<DiscoveredEndpoint> discover(EndpointQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        LinkedHashMap<String, DiscoveredEndpoint> byId = new LinkedHashMap<>();
        for (InterfaceDiscoveryProvider provider : providers()) {
            if (!provider.supports(query)) {
                continue;
            }
            List<DiscoveredEndpoint> discovered = provider.discover(query);
            if (discovered == null) {
                throw new InterfaceDiscoveryException("Provider " + provider.id() + " returned null");
            }
            for (DiscoveredEndpoint endpoint : discovered) {
                if (endpoint == null) {
                    throw new InterfaceDiscoveryException("Provider " + provider.id() + " returned a null endpoint");
                }
                byId.putIfAbsent(endpoint.endpointId(), endpoint);
            }
        }
        return List.copyOf(new ArrayList<>(byId.values()));
    }

    private static Comparator<InterfaceDiscoveryProvider> providerOrder() {
        return Comparator.comparingInt(InterfaceDiscoveryProvider::priority).reversed()
                .thenComparing(InterfaceDiscoveryProvider::id);
    }
}
