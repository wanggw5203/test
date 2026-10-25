package io.github.aitestflow.discovery;

import java.util.List;

/** Dubbo-specific contract resolved by a discovery provider. */
public record DubboEndpoint(
        String interfaceName,
        String methodName,
        List<String> parameterTypes,
        String version,
        String group
) {
    public DubboEndpoint {
        interfaceName = requireText(interfaceName, "interfaceName");
        methodName = requireText(methodName, "methodName");
        parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        version = version == null ? "" : version.trim();
        group = group == null ? "" : group.trim();
    }

    public String qualifiedMethod() {
        return interfaceName + "#" + methodName;
    }

    private static String requireText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
