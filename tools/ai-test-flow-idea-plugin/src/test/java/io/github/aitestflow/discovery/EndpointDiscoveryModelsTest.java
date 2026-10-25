package io.github.aitestflow.discovery;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointDiscoveryModelsTest {
    @Test
    void queryNormalizesOptionalBoundariesAndProtectsScopes() {
        ArrayList<String> scopes = new ArrayList<>(List.of(" service/src/main/java ", "service/src/main/java", ""));

        EndpointQuery query = new EndpointQuery(
                null, EndpointSelectorKind.API_CODE, " ORDER_CREATE ", " order-service ", scopes);
        scopes.add("later");

        assertEquals(EndpointProtocol.AUTO, query.protocol());
        assertEquals("ORDER_CREATE", query.value());
        assertEquals("order-service", query.module());
        assertEquals(List.of("service/src/main/java"), query.scopes());
        assertTrue(query.hasModuleBoundary());
        assertTrue(query.hasScopeBoundary());
        assertThrows(UnsupportedOperationException.class, () -> query.scopes().add("mutable"));
        assertThrows(IllegalArgumentException.class,
                () -> EndpointQuery.of(EndpointProtocol.HTTP, EndpointSelectorKind.HTTP_PATH, " "));
    }

    @Test
    void endpointSortsCallChainAndExposesTypedAssets() {
        JavaSymbolEvidence controller = symbol("com.example.OrderController", "create", 22);
        TypeEvidence request = new TypeEvidence(TypeRole.BO, "com.example.OrderCreateBO",
                "service/src/main/java/com/example/OrderCreateBO.java", "abc",
                List.of(new TypeFieldEvidence("skuId", "java.lang.Long", true,
                        List.of("NotNull"), "SKU")));
        DiscoveredEndpoint endpoint = new DiscoveredEndpoint(
                "order-create", EndpointProtocol.HTTP, "ORDER_CREATE", "Create order",
                new HttpEndpoint("post", "orders", List.of("application/json"), List.of("application/json")),
                null, controller, List.of(request),
                List.of(
                        new CallChainEvidence(2, CallChainKind.SERVICE,
                                symbol("com.example.OrderService", "create", 15), "calls"),
                        new CallChainEvidence(1, CallChainKind.CONTROLLER, controller, "entrypoint")
                ),
                Map.of("owner", "order-team"), "manual-catalog");

        assertEquals("POST /orders", endpoint.http().selectorValue());
        assertEquals(CallChainKind.CONTROLLER, endpoint.callChain().getFirst().kind());
        assertEquals(List.of(request), endpoint.typesWithRole(TypeRole.BO));
        assertThrows(UnsupportedOperationException.class, () -> endpoint.types().add(request));
    }

    @Test
    void resolvedEndpointCannotUseAutoOrMixedProtocolDetails() {
        JavaSymbolEvidence symbol = symbol("com.example.OrderService", "create", 1);
        assertThrows(IllegalArgumentException.class, () -> new DiscoveredEndpoint(
                "x", EndpointProtocol.AUTO, "", "", null, null, symbol,
                List.of(), List.of(), Map.of(), "test"));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveredEndpoint(
                "x", EndpointProtocol.HTTP, "", "",
                new HttpEndpoint("GET", "/orders", List.of(), List.of()),
                new DubboEndpoint("com.example.OrderService", "get", List.of(), "", ""), symbol,
                List.of(), List.of(), Map.of(), "test"));
    }

    private static JavaSymbolEvidence symbol(String className, String method, int line) {
        return new JavaSymbolEvidence("order-service", className, method,
                className + "#" + method + "()", "service/src/main/java/Example.java", line, "abc");
    }
}
