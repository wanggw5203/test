package io.github.aitestflow.discovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized result shared by PSI, OpenAPI, Dubbo metadata and manual discovery providers.
 * All paths are project-relative where possible so the result can be committed as evidence.
 */
public record DiscoveredEndpoint(
        String endpointId,
        EndpointProtocol protocol,
        String apiCode,
        String displayName,
        HttpEndpoint http,
        DubboEndpoint dubbo,
        JavaSymbolEvidence entrypoint,
        List<TypeEvidence> types,
        List<CallChainEvidence> callChain,
        Map<String, String> attributes,
        String discoveredBy
) {
    public DiscoveredEndpoint {
        endpointId = requireText(endpointId, "endpointId");
        protocol = Objects.requireNonNull(protocol, "protocol");
        if (protocol == EndpointProtocol.AUTO) {
            throw new IllegalArgumentException("A discovered endpoint must resolve to HTTP or DUBBO");
        }
        if (protocol == EndpointProtocol.HTTP && http == null) {
            throw new IllegalArgumentException("HTTP endpoint details are required");
        }
        if (protocol == EndpointProtocol.DUBBO && dubbo == null) {
            throw new IllegalArgumentException("Dubbo endpoint details are required");
        }
        if (protocol == EndpointProtocol.HTTP && dubbo != null
                || protocol == EndpointProtocol.DUBBO && http != null) {
            throw new IllegalArgumentException("Endpoint details must match the resolved protocol");
        }
        apiCode = text(apiCode);
        displayName = text(displayName);
        entrypoint = Objects.requireNonNull(entrypoint, "entrypoint");
        types = types == null ? List.of() : List.copyOf(types);
        callChain = callChain == null ? List.of() : callChain.stream()
                .sorted(java.util.Comparator.comparingInt(CallChainEvidence::order)).toList();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        discoveredBy = text(discoveredBy);
    }

    public List<TypeEvidence> typesWithRole(TypeRole role) {
        return types.stream().filter(type -> type.role() == role).toList();
    }

    private static String requireText(String value, String name) {
        String normalized = text(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
