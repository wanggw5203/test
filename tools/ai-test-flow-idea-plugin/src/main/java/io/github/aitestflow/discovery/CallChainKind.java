package io.github.aitestflow.discovery;

/** Common nodes that can appear in an HTTP or Dubbo implementation call chain. */
public enum CallChainKind {
    CONTROLLER,
    DUBBO_SERVICE,
    SERVICE,
    IMPLEMENTATION,
    DOMAIN,
    MAPPER,
    REPOSITORY,
    RPC_CLIENT,
    MESSAGE,
    OTHER
}
