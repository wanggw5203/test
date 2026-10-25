package io.github.aitestflow.discovery;

/** Provider failure that should be reported without silently fabricating endpoint evidence. */
public class InterfaceDiscoveryException extends RuntimeException {
    public InterfaceDiscoveryException(String message) {
        super(message);
    }

    public InterfaceDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
