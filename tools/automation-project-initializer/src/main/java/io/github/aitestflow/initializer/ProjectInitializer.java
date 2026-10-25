package io.github.aitestflow.initializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent, deterministic project templates. No IDE, AI or network dependency. */
public final class ProjectInitializer {
    public Map<String, String> preview(ProjectOptions options) { return Map.copyOf(ProjectTemplates.render(options)); }

    public List<Path> initialize(Path directory, ProjectOptions options) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (Files.exists(root) && !Files.isDirectory(root)) throw new IOException("目标不是目录：" + root);
        for (String build : List.of("pom.xml", "build.gradle", "build.gradle.kts")) {
            if (Files.exists(root.resolve(build))) throw new IOException("目标已存在构建工程，停止初始化，不覆盖：" + root.resolve(build));
        }
        Map<Path, String> outputs = new LinkedHashMap<>();
        for (var item : ProjectTemplates.render(options).entrySet()) {
            Path file = root.resolve(item.getKey()).normalize();
            if (!file.startsWith(root)) throw new IOException("模板路径越界");
            if (Files.exists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (item.getKey().equals(".gitignore")) continue; // Team-owned ignore rules are preserved.
                throw new IOException("目标文件已存在，未写入任何文件：" + file);
            }
            Path existing = file.getParent();
            while (existing != null && !Files.exists(existing)) existing = existing.getParent();
            if (existing != null && Files.exists(root) && !existing.toRealPath().startsWith(root.toRealPath()))
                throw new IOException("目标通过符号链接越出项目：" + file);
            outputs.put(file, item.getValue());
        }
        // CREATE_NEW also protects files created by another process after the preflight check.
        List<Path> created = new ArrayList<>();
        try {
            for (var item : outputs.entrySet()) {
                Files.createDirectories(item.getKey().getParent());
                Files.writeString(item.getKey(), item.getValue(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                created.add(item.getKey());
            }
        } catch (IOException failure) {
            // Remove only unchanged files written by this invocation; never remove directories or team edits.
            for (Path file : created) {
                try {
                    if (Files.readString(file).equals(outputs.get(file))) Files.delete(file);
                } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        return List.copyOf(created);
    }
}
