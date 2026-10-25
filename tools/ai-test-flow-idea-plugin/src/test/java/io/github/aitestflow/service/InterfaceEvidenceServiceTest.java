package io.github.aitestflow.service;

import io.github.aitestflow.discovery.EndpointProtocol;
import io.github.aitestflow.discovery.EndpointSelectorKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceEvidenceServiceTest {
    @Test
    void preservesUnresolvedHttpSelectorWithoutInventingCodeEvidence() {
        InterfaceEvidenceService service = new InterfaceEvidenceService(List.of());

        InterfaceEvidenceService.Evidence evidence = service.collect(
                EndpointProtocol.HTTP, EndpointSelectorKind.HTTP_PATH, "/api/orders");

        assertEquals("/api/orders", evidence.query().value());
        assertTrue(evidence.endpoints().isEmpty());
        assertTrue(evidence.json().contains("PROVIDER_PENDING"));
        assertTrue(evidence.json().contains("do not invent Java types or call chains"));
    }
}
