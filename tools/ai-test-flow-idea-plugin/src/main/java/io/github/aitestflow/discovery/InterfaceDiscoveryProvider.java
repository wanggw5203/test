package io.github.aitestflow.discovery;

import java.util.List;

/**
 * Extension point for locating project interfaces.
 *
 * <p>Implementations encapsulate their environment. For example, a future
 * {@code PsiInterfaceDiscoveryProvider} can receive an IntelliJ Project in its constructor, while
 * an OpenAPI provider can receive a catalog client. This keeps the pipeline independent of PSI.</p>
 */
public interface InterfaceDiscoveryProvider {
    String id();

    default int priority() {
        return 0;
    }

    boolean supports(EndpointQuery query);

    List<DiscoveredEndpoint> discover(EndpointQuery query) throws InterfaceDiscoveryException;
}
