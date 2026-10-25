package io.github.aitestflow.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import io.github.apitestkit.scaffold.ApiTestScaffoldGenerator;
import io.github.apitestkit.scaffold.GenerationSpec;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import com.google.gson.JsonParser;

/**
 * Project-scoped adapter around the bundled API scaffold generator.
 *
 * <p>Both the generation specification and every generated file live below the
 * opened project. Existing files are never overwritten.</p>
 */
public final class ScaffoldGenerationService {
    private final Path projectRoot;
    private final Runnable refreshProject;

    public ScaffoldGenerationService(Project project) {
        this(Path.of(project.getBasePath()), () ->
                LocalFileSystem.getInstance().refreshIoFiles(List.of(Path.of(project.getBasePath()).toFile())));
    }

    ScaffoldGenerationService(Path projectRoot) {
        this(projectRoot, () -> { });
    }

    private ScaffoldGenerationService(Path projectRoot, Runnable refreshProject) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.refreshProject = refreshProject;
    }

    public Result generate(String projectRelativeSpecPath, String testcasePath) throws IOException {
        Path cases = resolveProjectFile(testcasePath);
        if (!Files.isRegularFile(cases)) throw new IOException("上游测试用例不存在：" + testcasePath);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            Object parsed = new Yaml(new SafeConstructor(options)).load(Files.readString(cases));
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("TestCases") instanceof List<?> rows))
                throw new IOException("上游测试用例必须包含 TestCases 列表");
            Set<String> ids = new LinkedHashSet<>();
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> item) || !(item.get("caseId") instanceof String id)
                        || id.isBlank() || !ids.add(id)) throw new IOException("上游 caseId 缺失或重复");
            }
            GenerationSpec spec = new ApiTestScaffoldGenerator().loadSpec(resolveProjectFile(projectRelativeSpecPath));
            if (ids.isEmpty() || !ids.containsAll(spec.getSourceCaseIds()))
                throw new IOException("接口规格引用了上游用例中不存在的 sourceCaseId；已停止生成。");
        } catch (org.yaml.snakeyaml.error.YAMLException error) {
            throw new IOException("上游用例 YAML 无效", error);
        }
        return generate(projectRelativeSpecPath);
    }

    public Result generate(String projectRelativeSpecPath) throws IOException {
        Path specFile = resolveProjectFile(projectRelativeSpecPath);
        if (!Files.isRegularFile(specFile)) {
            throw new IOException("接口生成规格不存在：" + projectRelativeSpecPath);
        }

        ApiTestScaffoldGenerator generator = new ApiTestScaffoldGenerator();
        GenerationSpec spec = generator.loadSpec(specFile);
        if ("generic".equals(spec.profile())) throw new IOException("插件仅生成适配当前框架的 basetest-http / basetest-dubbo 骨架，不接受 generic 自由模板。");
        AutomationProjectCheck.verify(projectRoot);
        Path previousManifest = projectRoot.resolve(spec.getGenerator().getMetadataRoot())
                .resolve(spec.getApiCode())
                .resolve("scaffold-manifest.yaml")
                .normalize();

        relative(previousManifest);
        if (Files.isRegularFile(previousManifest)) {
            ApiTestScaffoldGenerator.ValidationReport report =
                    generator.validate(projectRoot, previousManifest, true);
            if (!report.isSuccess()) {
                throw new IOException("检测到上次生成后被修改或缺失的脚手架文件，已停止覆盖。missing="
                        + report.getMissingFiles() + ", modified=" + report.getModifiedFiles()
                        + ", malformed=" + report.getMalformedEntries());
            }
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(previousManifest));
            if (!(parsed instanceof Map<?, ?> manifest)) throw new IOException("脚手架清单格式无效");
            if (!matchesExistingOutputs(generator, specFile, previousManifest, manifest)) {
                throw new IOException("规格的实际生成结果与已有产物不同，请评审后使用新输出目录或接口版本；已保留现有源码与数据。");
            }
            List<String> paths = new ArrayList<>();
            for (Object item : (List<?>) manifest.get("files")) {
                String path = String.valueOf(((Map<?, ?>) item).get("path"));
                resolveProjectFile(path); paths.add(path);
            }
            paths.add(relative(previousManifest));
            String plan = String.valueOf(manifest.get("executableizationPlan"));
            resolveProjectFile(plan);
            refreshProject.run();
            return new Result(spec.getApiCode(), relative(previousManifest), plan, List.copyOf(paths));
        }

        ApiTestScaffoldGenerator.GenerationResult generated =
                generator.generate(specFile, projectRoot, false);
        ApiTestScaffoldGenerator.ValidationReport verification =
                generator.validate(projectRoot, generated.getManifest(), true);
        if (!verification.isSuccess()) {
            throw new IOException("脚手架生成后校验失败：missing=" + verification.getMissingFiles()
                    + ", modified=" + verification.getModifiedFiles()
                    + ", malformed=" + verification.getMalformedEntries());
        }
        refreshProject.run();
        return new Result(generated.getApiCode(), relative(generated.getManifest()),
                relative(generated.getExecutableizationPlan()),
                generated.getFiles().stream().map(this::relative).toList());
    }

    private boolean matchesExistingOutputs(ApiTestScaffoldGenerator generator, Path specFile,
                                            Path manifestFile, Map<?, ?> manifest) throws IOException {
        Map<Path, String> proposed = generator.preview(specFile, projectRoot);
        Set<Path> owned = new LinkedHashSet<>();
        for (Object item : (List<?>) manifest.get("files")) {
            owned.add(resolveProjectFile(String.valueOf(((Map<?, ?>) item).get("path"))));
        }
        Set<Path> targets = new LinkedHashSet<>(proposed.keySet());
        targets.remove(manifestFile);
        if (!targets.equals(owned)) return false;
        for (Path target : targets) {
            if (!sameContent(target, Files.readString(target), proposed.get(target))) return false;
        }
        return true;
    }

    private static boolean sameContent(Path path, String existing, String proposed) {
        String name = path.getFileName().toString();
        if (name.endsWith(".yaml")) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            return java.util.Objects.equals(yaml.load(existing), yaml.load(proposed));
        }
        if (name.endsWith(".json5")) {
            // Generated JSON5 only adds a leading single-line description to valid JSON.
            String left = existing.replaceFirst("^//[^\\r\\n]*[\\r\\n]+", "");
            String right = proposed.replaceFirst("^//[^\\r\\n]*[\\r\\n]+", "");
            return JsonParser.parseString(left).equals(JsonParser.parseString(right));
        }
        String left = existing.replace("\r\n", "\n");
        String right = proposed.replace("\r\n", "\n");
        if (name.endsWith(".java")) {
            left = left.replaceAll("(@create )\\d{4}-\\d{2}-\\d{2}", "$1<date>");
            right = right.replaceAll("(@create )\\d{4}-\\d{2}-\\d{2}", "$1<date>");
        }
        return left.equals(right);
    }

    private Path resolveProjectFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IOException("接口生成规格路径为空");
        }
        Path resolved = projectRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new IOException("接口生成规格必须位于当前项目内：" + relativePath);
        }
        return resolved;
    }

    private String relative(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(projectRoot)) {
            throw new IllegalArgumentException("生成器产物越出当前项目：" + path);
        }
        return projectRoot.relativize(normalized).toString().replace('\\', '/');
    }

    public record Result(String apiCode, String manifestPath, String executableizationPlanPath,
                         List<String> generatedFiles) { }
}
