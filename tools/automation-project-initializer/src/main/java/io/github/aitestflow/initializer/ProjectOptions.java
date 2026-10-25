package io.github.aitestflow.initializer;

import java.util.Objects;
import javax.lang.model.SourceVersion;

public record ProjectOptions(Type type, String groupId, String artifactId, String packageName, String frameworkVersion) {
    public enum Type { HTTP, DUBBO, SDK }

    public ProjectOptions {
        Objects.requireNonNull(type, "project type");
        if (groupId == null || !groupId.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
            throw new IllegalArgumentException("groupId 必须是有效 Maven 分组");
        if (artifactId == null || !artifactId.matches("[A-Za-z][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("artifactId 必须以字母开头，只允许字母、数字、下划线和连字符");
        if (packageName == null || !SourceVersion.isName(packageName, SourceVersion.RELEASE_17))
            throw new IllegalArgumentException("packageName 必须是有效 Java 包名，不能包含保留字");
        if (frameworkVersion == null || !frameworkVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))
            throw new IllegalArgumentException("框架版本号无效");
    }
}
