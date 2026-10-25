package io.github.aitestflow.enterprise;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.project.Project;
import io.github.aitestflow.service.ArtifactStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceManager {
    public static final String CONFIG_PATH = ".ai-test-flow/workspace.json";
    private final Project project;
    private final ArtifactStore store;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WorkspaceManager(Project project, ArtifactStore store) {
        this.project = project;
        this.store = store;
    }

    public boolean isInitialized() {
        return project.getBasePath() != null && Files.isRegularFile(Path.of(project.getBasePath(), CONFIG_PATH));
    }

    public WorkspaceConfig load() throws IOException {
        if (!isInitialized()) return new WorkspaceConfig();
        return parseConfiguration(Files.readString(store.resolve(CONFIG_PATH), StandardCharsets.UTF_8));
    }

    static WorkspaceConfig parseConfiguration(String content) throws IOException {
        if (content == null || content.isBlank()) return new WorkspaceConfig();
        WorkspaceConfig config;
        try {
            config = new Gson().fromJson(content, WorkspaceConfig.class);
        } catch (RuntimeException exception) {
            throw new IOException("工作区配置 JSON 格式错误，请修复 " + CONFIG_PATH + "：" + exception.getMessage(), exception);
        }
        if (config == null || config.paths == null || config.gates == null || config.automation == null) {
            throw new IOException("工作区配置缺少必要对象，请检查 " + CONFIG_PATH + " 中的 paths、gates、automation");
        }
        return config;
    }

    public Path initialize() throws IOException {
        WorkspaceConfig config = isInitialized() ? load() : new WorkspaceConfig();
        boolean needsDefaults = !isInitialized()
                || Files.readString(store.resolve(CONFIG_PATH), StandardCharsets.UTF_8).isBlank();
        Path path = needsDefaults ? store.save(CONFIG_PATH, gson.toJson(config)) : store.resolve(CONFIG_PATH);
        if (!Files.exists(store.resolve(".ai-test-flow/README.md"))) store.save(".ai-test-flow/README.md", """
                # AI Test Flow 企业工作区

                本目录保存可审计的运行记录和资产清单，不保存 API Key。所有正式产物均应通过 Git 评审后合并。

                - `workspace.json`：团队共享规范
                - `quality-assets/inputs/`：纳入 Git 的原始需求材料
                - `quality-assets/evidence/`：纳入 Git 的接口、代码、页面与执行证据
                - `manifests/`：每个需求的最新资产关系
                - `runs/`：每次生成的不可变运行记录
                - `rejected/`：未通过质量门禁的候选产物
                """);
        ensureMarker(config.paths.inputs);
        ensureMarker(config.paths.evidence);
        ensureMarker(config.paths.analysis);
        ensureMarker(config.paths.testcases);
        ensureMarker(config.paths.automation);
        ensureMarker(config.paths.manifests);
        ensureMarker(config.paths.runs);
        return path;
    }

    private void ensureMarker(String directory) throws IOException {
        Path marker = Path.of(directory, ".gitkeep");
        if (!Files.exists(store.resolve(marker.toString()))) store.save(marker.toString(), "");
    }
}
