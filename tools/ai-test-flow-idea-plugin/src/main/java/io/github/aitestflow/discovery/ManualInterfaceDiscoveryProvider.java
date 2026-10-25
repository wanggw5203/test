package io.github.aitestflow.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory catalog used before a real project PSI provider is available and for explicit endpoint
 * contracts checked into Git. It never infers or invents source evidence.
 */
public final class ManualInterfaceDiscoveryProvider implements InterfaceDiscoveryProvider {
    public static final String PROVIDER_ID = "manual-catalog";

    private final CopyOnWriteArrayList<DiscoveredEndpoint> catalog = new CopyOnWriteArrayList<>();

    public ManualInterfaceDiscoveryProvider() {
    }

    public ManualInterfaceDiscoveryProvider(Collection<DiscoveredEndpoint> endpoints) {
        if (endpoints != null) {
            catalog.addAll(endpoints);
        }
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(EndpointQuery query) {
        return query != null;
    }

    @Override
    public List<DiscoveredEndpoint> discover(EndpointQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        return catalog.stream()
                .filter(endpoint -> protocolMatches(query, endpoint))
                .filter(endpoint -> moduleMatches(query, endpoint))
                .filter(endpoint -> scopeMatches(query, endpoint))
                .filter(endpoint -> selectorMatches(query, endpoint))
                .toList();
    }

    public void register(DiscoveredEndpoint endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint must not be null");
        }
        catalog.removeIf(existing -> existing.endpointId().equals(endpoint.endpointId()));
        catalog.add(endpoint);
    }

    public boolean unregister(String endpointId) {
        return endpointId != null && catalog.removeIf(endpoint -> endpoint.endpointId().equals(endpointId));
    }

    public List<DiscoveredEndpoint> endpoints() {
        return List.copyOf(catalog);
    }

    private static boolean protocolMatches(EndpointQuery query, DiscoveredEndpoint endpoint) {
        return query.protocol() == EndpointProtocol.AUTO || query.protocol() == endpoint.protocol();
    }

    private static boolean moduleMatches(EndpointQuery query, DiscoveredEndpoint endpoint) {
        return !query.hasModuleBoundary()
                || endpoint.entrypoint().module().equalsIgnoreCase(query.module());
    }

    private static boolean scopeMatches(EndpointQuery query, DiscoveredEndpoint endpoint) {
        if (!query.hasScopeBoundary()) {
            return true;
        }
        String sourcePath = normalize(endpoint.entrypoint().sourcePath());
        return query.scopes().stream().map(ManualInterfaceDiscoveryProvider::normalize)
                .anyMatch(scope -> sourcePath.equals(scope) || sourcePath.startsWith(scope + "/"));
    }

    private static boolean selectorMatches(EndpointQuery query, DiscoveredEndpoint endpoint) {
        String expected = normalizeValue(query.value());
        return switch (query.selectorKind()) {
            case API_CODE -> normalizeValue(endpoint.apiCode()).equals(expected);
            case HTTP_PATH -> endpoint.http() != null && httpMatches(expected, endpoint.http());
            case JAVA_SYMBOL -> symbolMatches(expected, endpoint.entrypoint());
            case DUBBO_METHOD -> endpoint.dubbo() != null && dubboMatches(expected, endpoint.dubbo());
        };
    }

    private static boolean httpMatches(String expected, HttpEndpoint http) {
        String path = normalizeValue(http.path());
        String methodAndPath = normalizeValue(http.selectorValue());
        return expected.equals(path) || expected.equals(methodAndPath);
    }

    private static boolean symbolMatches(String expected, JavaSymbolEvidence symbol) {
        return expected.equals(normalizeValue(symbol.className()))
                || expected.equals(normalizeValue(symbol.qualifiedSymbol()))
                || (!symbol.signature().isEmpty() && expected.equals(normalizeValue(symbol.signature())));
    }

    private static boolean dubboMatches(String expected, DubboEndpoint dubbo) {
        return expected.equals(normalizeValue(dubbo.methodName()))
                || expected.equals(normalizeValue(dubbo.qualifiedMethod()))
                || expected.equals(normalizeValue(dubbo.interfaceName() + "." + dubbo.methodName()));
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
