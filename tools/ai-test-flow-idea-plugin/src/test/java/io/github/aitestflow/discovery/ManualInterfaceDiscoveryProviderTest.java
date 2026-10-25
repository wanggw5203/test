package io.github.aitestflow.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualInterfaceDiscoveryProviderTest {
    @Test
    void locatesHttpEndpointByCodePathAndJavaSymbolWithinBoundaries() {
        DiscoveredEndpoint http = httpEndpoint();
        ManualInterfaceDiscoveryProvider provider = new ManualInterfaceDiscoveryProvider(List.of(http, dubboEndpoint()));

        assertEquals(List.of(http), provider.discover(EndpointQuery.of(
                EndpointProtocol.AUTO, EndpointSelectorKind.API_CODE, "order_create")));
        assertEquals(List.of(http), provider.discover(EndpointQuery.httpPath("POST /orders")));
        assertEquals(List.of(http), provider.discover(EndpointQuery.of(
                EndpointProtocol.HTTP, EndpointSelectorKind.JAVA_SYMBOL,
                "com.example.OrderController#create")));
        assertEquals(List.of(http), provider.discover(new EndpointQuery(
                EndpointProtocol.HTTP, EndpointSelectorKind.HTTP_PATH, "/orders",
                "order-service", List.of("service/src/main/java"))));
        assertTrue(provider.discover(new EndpointQuery(
                EndpointProtocol.HTTP, EndpointSelectorKind.HTTP_PATH, "/orders",
                "inventory-service", List.of())).isEmpty());
    }

    @Test
    void locatesDubboEndpointAndReplacesManualCatalogEntryByStableId() {
        DiscoveredEndpoint dubbo = dubboEndpoint();
        ManualInterfaceDiscoveryProvider provider = new ManualInterfaceDiscoveryProvider();
        provider.register(dubbo);

        assertEquals(List.of(dubbo), provider.discover(EndpointQuery.dubboMethod(
                "com.example.OrderFacade#create")));
        assertEquals(List.of(dubbo), provider.discover(EndpointQuery.dubboMethod("create")));

        DiscoveredEndpoint replacement = new DiscoveredEndpoint(
                dubbo.endpointId(), dubbo.protocol(), "ORDER_CREATE_V2", dubbo.displayName(),
                null, dubbo.dubbo(), dubbo.entrypoint(), dubbo.types(), dubbo.callChain(),
                dubbo.attributes(), dubbo.discoveredBy());
        provider.register(replacement);

        assertEquals(1, provider.endpoints().size());
        assertEquals("ORDER_CREATE_V2", provider.endpoints().getFirst().apiCode());
        assertTrue(provider.unregister(dubbo.endpointId()));
        assertTrue(provider.endpoints().isEmpty());
    }

    static DiscoveredEndpoint httpEndpoint() {
        JavaSymbolEvidence entry = new JavaSymbolEvidence("order-service",
                "com.example.OrderController", "create", "create(OrderCreateBO)",
                "service/src/main/java/com/example/OrderController.java", 42, "hash-http");
        return new DiscoveredEndpoint(
                "http-order-create", EndpointProtocol.HTTP, "ORDER_CREATE", "Create order",
                new HttpEndpoint("POST", "/orders", List.of("application/json"), List.of("application/json")),
                null, entry, List.of(), List.of(), Map.of(), ManualInterfaceDiscoveryProvider.PROVIDER_ID);
    }

    static DiscoveredEndpoint dubboEndpoint() {
        JavaSymbolEvidence entry = new JavaSymbolEvidence("order-api",
                "com.example.OrderFacade", "create", "create(OrderCreateDTO)",
                "api/src/main/java/com/example/OrderFacade.java", 12, "hash-dubbo");
        return new DiscoveredEndpoint(
                "dubbo-order-create", EndpointProtocol.DUBBO, "ORDER_DUBBO_CREATE", "Create order RPC",
                null, new DubboEndpoint("com.example.OrderFacade", "create",
                List.of("com.example.OrderCreateDTO"), "1.0.0", "order"), entry,
                List.of(), List.of(), Map.of(), ManualInterfaceDiscoveryProvider.PROVIDER_ID);
    }
}
