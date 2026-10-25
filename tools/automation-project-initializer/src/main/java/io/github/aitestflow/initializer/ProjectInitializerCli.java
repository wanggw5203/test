package io.github.aitestflow.initializer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ProjectInitializerCli {
    public static void main(String[] args) {
        try {
            if (args.length == 0 || args[0].equals("--help")) {
                System.out.println("java -jar automation-project-initializer.jar --type HTTP|DUBBO|SDK --directory DIR --artifact-id NAME [--group-id com.example] [--package com.example.automation] [--framework-version 1.1.0-SNAPSHOT]");
                return;
            }
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length || !java.util.Set.of("--type", "--directory", "--artifact-id", "--group-id", "--package", "--framework-version").contains(args[i])
                        || values.put(args[i], args[i + 1]) != null) throw new IllegalArgumentException("参数无效或重复：" + args[i]);
            }
            var options = new ProjectOptions(ProjectOptions.Type.valueOf(required(values, "--type").toUpperCase(Locale.ROOT)),
                    values.getOrDefault("--group-id", "com.example"), required(values, "--artifact-id"),
                    values.getOrDefault("--package", "com.example.automation"), values.getOrDefault("--framework-version", "1.1.0-SNAPSHOT"));
            Path root = Path.of(required(values, "--directory"));
            var files = new ProjectInitializer().initialize(root, options);
            System.out.println("Created " + options.type() + " automation project: " + root.toAbsolutePath() + " (" + files.size() + " files)");
            System.out.println("Next: mvn -f \"" + root.resolve("pom.xml") + "\" test (initialization smoke test only; no real service calls)");
        } catch (Exception error) {
            System.err.println("初始化失败：" + error.getMessage());
            System.exit(1);
        }
    }
    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少参数：" + key);
        return value;
    }
}
