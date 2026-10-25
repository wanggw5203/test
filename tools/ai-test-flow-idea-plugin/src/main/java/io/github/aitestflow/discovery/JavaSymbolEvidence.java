package io.github.aitestflow.discovery;

/** A source-backed Java method/class reference used as traceable discovery evidence. */
public record JavaSymbolEvidence(
        String module,
        String className,
        String methodName,
        String signature,
        String sourcePath,
        int line,
        String sha256
) {
    public JavaSymbolEvidence {
        module = text(module);
        className = requireText(className, "className");
        methodName = text(methodName);
        signature = text(signature);
        sourcePath = text(sourcePath).replace('\\', '/');
        sha256 = text(sha256);
        if (line < 0) {
            throw new IllegalArgumentException("line must be zero (unknown) or positive");
        }
    }

    public String qualifiedSymbol() {
        return methodName.isEmpty() ? className : className + "#" + methodName;
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
