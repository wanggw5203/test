package io.github.aitestflow.enterprise;

import com.intellij.openapi.project.Project;
import io.github.aitestflow.service.ArtifactStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 将所有业务输入先登记为项目资产。插件只做转换，不把本机临时文件当作事实来源。
 */
public final class InputAssetService {
    private final Path projectRoot;
    private final ArtifactStore store;

    public InputAssetService(Project project, ArtifactStore store) {
        this.projectRoot = Path.of(project.getBasePath()).toAbsolutePath().normalize();
        this.store = store;
    }

    public List<Path> registerFiles(List<Path> sources, WorkspaceConfig config) throws IOException {
        List<Path> managed = new ArrayList<>();
        for (Path source : sources) managed.add(registerFile(source, config));
        return List.copyOf(managed);
    }

    public Path registerFile(Path source, WorkspaceConfig config) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) throw new IOException("输入文件不存在: " + absolute);
        if (absolute.startsWith(projectRoot)) return absolute;
        byte[] bytes = Files.readAllBytes(absolute);
        String filename = safeName(absolute.getFileName().toString());
        String target = normalize(Path.of(config.paths.inputs, withHash(filename, ArtifactStore.sha256(bytes).substring(0, 12))));
        Path resolved = store.resolve(target);
        if (!Files.exists(resolved) || !ArtifactStore.capture(resolved).sha256().equals(ArtifactStore.sha256(bytes))) {
            store.saveBinary(target, bytes);
        }
        return resolved;
    }

    public Path registerManualRequirement(String content, WorkspaceConfig config) throws IOException {
        if (content == null || content.isBlank()) throw new IOException("手工输入的需求为空");
        String hash = ArtifactStore.sha256(content).substring(0, 12);
        String target = normalize(Path.of(config.paths.inputs, "manual", "manual-prd-" + hash + ".prd.txt"));
        Path path = store.resolve(target);
        if (!Files.exists(path)) store.save(target, content);
        return path;
    }

    public Path registerEvidence(String assetId, String content, WorkspaceConfig config) throws IOException {
        if (content == null || content.isBlank()) return null;
        String hash = ArtifactStore.sha256(content).substring(0, 12);
        String target = normalize(Path.of(config.paths.evidence, assetId + "-" + hash + ".evidence.md"));
        Path path = store.resolve(target);
        if (!Files.exists(path)) store.save(target, content);
        return path;
    }

    public String relative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(projectRoot)) throw new IllegalArgumentException("输入未进入项目: " + absolute);
        return normalize(projectRoot.relativize(absolute));
    }

    private static String withHash(String filename, String hash) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename + "-" + hash : filename.substring(0, dot) + "-" + hash + filename.substring(dot);
    }

    private static String safeName(String value) {
        String sanitized = value.replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        return sanitized.isBlank() ? "input.bin" : sanitized;
    }

    private static String normalize(Path path) { return path.toString().replace('\\', '/'); }
}
