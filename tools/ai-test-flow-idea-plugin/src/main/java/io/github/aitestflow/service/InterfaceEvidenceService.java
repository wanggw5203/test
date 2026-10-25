package io.github.aitestflow.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aitestflow.discovery.DiscoveredEndpoint;
import io.github.aitestflow.discovery.EndpointProtocol;
import io.github.aitestflow.discovery.EndpointQuery;
import io.github.aitestflow.discovery.EndpointSelectorKind;
import io.github.aitestflow.discovery.InterfaceDiscoveryProvider;
import io.github.aitestflow.discovery.InterfaceDiscoveryRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Converts an interface-selection request and provider results into immutable evidence for the
 * generation prompt and the Git-managed evidence asset.
 *
 * <p>Future PSI/UAST, OpenAPI, Dubbo metadata or company-catalog implementations only need to
 * implement {@link InterfaceDiscoveryProvider}. Providers bundled in the plugin can be exposed
 * through Java's service-provider mechanism; tests and future project integrations can register
 * providers explicitly.</p>
 */
public final class InterfaceEvidenceService {
    private final InterfaceDiscoveryRegistry registry;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public InterfaceEvidenceService() {
        this(loadBundledProviders());
    }

    InterfaceEvidenceService(List<InterfaceDiscoveryProvider> providers) {
        this.registry = new InterfaceDiscoveryRegistry(providers);
    }

    public void register(InterfaceDiscoveryProvider provider) {
        registry.register(provider);
    }

    public Evidence collect(EndpointProtocol protocol, EndpointSelectorKind selectorKind, String value) {
        EndpointQuery query = EndpointQuery.of(protocol, selectorKind, value);
        List<DiscoveredEndpoint> endpoints = registry.discover(query);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("kind", "interface-discovery-evidence");
        document.put("query", query);
        document.put("status", endpoints.isEmpty() ? "PROVIDER_PENDING" : "RESOLVED");
        document.put("providers", registry.providers().stream().map(InterfaceDiscoveryProvider::id).toList());
        document.put("endpoints", endpoints);
        document.put("evidenceRule", endpoints.isEmpty()
                ? "No project discovery provider is installed yet. Preserve the selector and do not invent Java types or call chains."
                : "Use only source-backed types and call-chain evidence returned by providers.");
        return new Evidence(query, endpoints, gson.toJson(document));
    }

    private static List<InterfaceDiscoveryProvider> loadBundledProviders() {
        return ServiceLoader.load(InterfaceDiscoveryProvider.class,
                        InterfaceEvidenceService.class.getClassLoader())
                .stream().map(ServiceLoader.Provider::get).toList();
    }

    public record Evidence(EndpointQuery query, List<DiscoveredEndpoint> endpoints, String json) { }
}
