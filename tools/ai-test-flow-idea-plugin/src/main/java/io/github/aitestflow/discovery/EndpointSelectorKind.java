package io.github.aitestflow.discovery;

/** Stable selector types accepted by every interface-discovery implementation. */
public enum EndpointSelectorKind {
    API_CODE,
    HTTP_PATH,
    JAVA_SYMBOL,
    DUBBO_METHOD
}
