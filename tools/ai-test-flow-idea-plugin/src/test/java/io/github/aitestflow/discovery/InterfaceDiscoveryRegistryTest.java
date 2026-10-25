package io.github.aitestflow.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterfaceDiscoveryRegistryTest {
    @Test
    void higherPriorityProviderWinsWhenStableEndpointIdsOverlap() {
        DiscoveredEndpoint manual = ManualInterfaceDiscoveryProviderTest.httpEndpoint();
        DiscoveredEndpoint psi = copyWithDiscoverySource(manual, "project-psi");
        InterfaceDiscoveryRegistry registry = new InterfaceDiscoveryRegistry(List.of(
                provider("manual", 10, manual),
                provider("psi", 100, psi)
        ));

        List<DiscoveredEndpoint> result = registry.discover(EndpointQuery.httpPath("/orders"));

        assertEquals(1, result.size());
        assertEquals("project-psi", result.getFirst().discoveredBy());
        assertEquals(List.of("psi", "manual"), registry.providers().stream().map(InterfaceDiscoveryProvider::id).toList());
    }

    @Test
    void registryRejectsBrokenProviderContract() {
        InterfaceDiscoveryRegistry registry = new InterfaceDiscoveryRegistry(List.of(new InterfaceDiscoveryProvider() {
            @Override public String id() { return "broken"; }
            @Override public boolean supports(EndpointQuery query) { return true; }
            @Override public List<DiscoveredEndpoint> discover(EndpointQuery query) { return null; }
        }));

        assertThrows(InterfaceDiscoveryException.class,
                () -> registry.discover(EndpointQuery.httpPath("/orders")));
    }

    private static InterfaceDiscoveryProvider provider(String id, int priority, DiscoveredEndpoint endpoint) {
        return new InterfaceDiscoveryProvider() {
            @Override public String id() { return id; }
            @Override public int priority() { return priority; }
            @Override public boolean supports(EndpointQuery query) { return true; }
            @Override public List<DiscoveredEndpoint> discover(EndpointQuery query) { return List.of(endpoint); }
        };
    }

    private static DiscoveredEndpoint copyWithDiscoverySource(DiscoveredEndpoint endpoint, String source) {
        return new DiscoveredEndpoint(endpoint.endpointId(), endpoint.protocol(), endpoint.apiCode(),
                endpoint.displayName(), endpoint.http(), endpoint.dubbo(), endpoint.entrypoint(), endpoint.types(),
                endpoint.callChain(), endpoint.attributes(), source);
    }
}
