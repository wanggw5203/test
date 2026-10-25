package io.github.aitestflow.enterprise;

import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.service.ArtifactStore;

import java.nio.file.Path;
import java.util.Locale;

public record AssetPlan(String assetId, String sourcePath, String tadPath, String testcasePath,
                        String apiSpecPath, String webAtcPath, String feedbackPath, String manifestPath) {
    public static AssetPlan forSource(Path projectRoot, Path source, WorkspaceConfig config) {
        Path absolute = source.toAbsolutePath().normalize();
        String relative = absolute.startsWith(projectRoot) ? normalize(projectRoot.relativize(absolute)) : absolute.toString();
        String filename = absolute.getFileName().toString();
        String base = baseName(filename).replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        String assetId = base + "-" + ArtifactStore.sha256(relative.toLowerCase(Locale.ROOT)).substring(0, 8);
        boolean markdown = filename.toLowerCase(Locale.ROOT).endsWith(".md") || filename.toLowerCase(Locale.ROOT).endsWith(".markdown");
        Path managedInputRoot = projectRoot.resolve(config.paths.inputs).normalize();
        String tad = markdown && absolute.startsWith(projectRoot) && !absolute.startsWith(managedInputRoot)
                && "IN_PLACE_MARKDOWN".equals(config.tadWriteMode)
                ? relative : normalize(Path.of(config.paths.analysis, assetId + ".tad.md"));
        String cases = normalize(Path.of(config.paths.testcases, assetId + ".testcases.yaml"));
        return new AssetPlan(assetId, relative, tad, cases,
                normalize(Path.of(config.paths.automation, assetId + ".generation-spec.yaml")),
                normalize(Path.of(config.paths.automation, assetId + ".atc.yaml")),
                normalize(Path.of(config.paths.automation, assetId + ".feedback.yaml")),
                normalize(Path.of(config.paths.manifests, assetId + ".manifest.json")));
    }

    public String pathFor(PipelineStage stage) {
        return switch (stage) {
            case REQUIREMENT_TO_TAD -> tadPath;
            case TAD_TO_TEST_CASES -> testcasePath;
            case TEST_CASES_TO_API_SPEC -> apiSpecPath;
            case TEST_CASES_TO_WEB_ATC -> webAtcPath;
            case FEEDBACK -> feedbackPath;
        };
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

    private static String normalize(Path path) { return path.toString().replace('\\', '/'); }
}
