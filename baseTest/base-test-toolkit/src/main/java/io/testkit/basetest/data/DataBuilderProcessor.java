package io.testkit.basetest.data;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Generates TestNG adapters without modifying the original data-builder source class. */
@SupportedAnnotationTypes("io.testkit.basetest.data.DataBuilder")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class DataBuilderProcessor extends AbstractProcessor {
    public static final String ADAPTER_SUFFIX = "_DataBuilderAdapter";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, List<ExecutableElement>> grouped = new LinkedHashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(DataBuilder.class)) {
            if (element.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) element;
            TypeElement owner = (TypeElement) method.getEnclosingElement();
            if (valid(method, owner)) grouped.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(method);
        }
        grouped.forEach(this::generate);
        return false;
    }

    private boolean valid(ExecutableElement method, TypeElement owner) {
        Set<Modifier> modifiers = method.getModifiers();
        boolean valid = modifiers.contains(Modifier.PUBLIC)
                && !modifiers.contains(Modifier.STATIC)
                && !modifiers.contains(Modifier.FINAL)
                && !owner.getModifiers().contains(Modifier.FINAL);
        if (!valid) processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@DataBuilder requires a public, non-static, non-final method in a non-final class", method);
        return valid;
    }

    private void generate(TypeElement owner, List<ExecutableElement> methods) {
        PackageElement pkg = processingEnv.getElementUtils().getPackageOf(owner);
        String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
        String ownerName = owner.getSimpleName().toString();
        String adapterName = ownerName + ADAPTER_SUFFIX;
        String qualifiedName = packageName.isEmpty() ? adapterName : packageName + "." + adapterName;
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject source = filer.createSourceFile(qualifiedName, owner);
            try (Writer writer = source.openWriter()) {
                writer.write(source(packageName, ownerName, adapterName, methods));
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Cannot generate " + qualifiedName + ": " + e.getMessage(), owner);
        }
    }

    private String source(String packageName, String ownerName, String adapterName,
                          List<ExecutableElement> methods) {
        StringBuilder out = new StringBuilder();
        if (!packageName.isEmpty()) out.append("package ").append(packageName).append(";\n\n");
        out.append("@javax.annotation.processing.Generated(\"")
                .append(getClass().getName()).append("\")\n")
                .append("public class ").append(adapterName).append(" extends ").append(ownerName).append(" {\n");
        for (ExecutableElement method : methods) append(out, method);
        return out.append("}\n").toString();
    }

    private void append(StringBuilder out, ExecutableElement method) {
        DataBuilder data = method.getAnnotation(DataBuilder.class);
        out.append("  @org.testng.annotations.Test(description = ").append(quote(data.description()))
                .append(", enabled = ").append(data.enabled())
                .append(", priority = ").append(data.priority());
        if (!data.dataProvider().isBlank()) {
            out.append(", dataProvider = ").append(quote(data.dataProvider()));
        }
        if (data.groups().length > 0) {
            out.append(", groups = {")
                    .append(java.util.Arrays.stream(data.groups()).map(this::quote).collect(Collectors.joining(", ")))
                    .append("}");
        }
        out.append(")\n  @Override\n  public ").append(method.getReturnType()).append(" ")
                .append(method.getSimpleName()).append("(");
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parameters.get(i).asType()).append(" arg").append(i);
        }
        out.append(") {\n    ");
        if (method.getReturnType().getKind() != javax.lang.model.type.TypeKind.VOID) out.append("return ");
        out.append("super.").append(method.getSimpleName()).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) out.append(", ");
            out.append("arg").append(i);
        }
        out.append(");\n  }\n");
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
