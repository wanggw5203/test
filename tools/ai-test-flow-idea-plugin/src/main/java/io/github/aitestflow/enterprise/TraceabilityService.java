package io.github.aitestflow.enterprise;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.service.ArtifactStore;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TraceabilityService {
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final ArtifactStore store;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public TraceabilityService(ArtifactStore store) { this.store = store; }

    public RunSession begin(AssetPlan plan, String sourceContent, List<String> inputPaths, String evidencePath,
                            String mode, String model, WorkspaceConfig config) throws IOException {
        String runId = RUN_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new RunSession(plan, config, runId, sourceContent, inputPaths, evidencePath, mode, model);
    }

    public final class RunSession {
        private final AssetPlan plan;
        private final WorkspaceConfig config;
        private final String runId;
        private final Instant startedAt = Instant.now();
        private final Map<String, Object> root = new LinkedHashMap<>();
        private final List<Map<String, Object>> stages = new ArrayList<>();

        private RunSession(AssetPlan plan, WorkspaceConfig config, String runId, String sourceContent,
                           List<String> inputPaths, String evidencePath, String mode, String model) throws IOException {
            this.plan = plan;
            this.config = config;
            this.runId = runId;
            root.put("schemaVersion", 1);
            root.put("runId", runId);
            root.put("assetId", plan.assetId());
            root.put("workspaceId", config.workspaceId);
            root.put("team", config.team);
            root.put("pluginVersion", "1.6.1");
            root.put("mode", mode);
            if ("GOLDEN_DEMO".equals(mode)) root.put("goldenDataset", "order-create-v1");
            root.put("model", model);
            root.put("automationContract", Map.of(
                    "framework", config.automation.framework,
                    "handoffMode", config.automation.handoffMode,
                    "executeFromPlugin", config.automation.executeFromPlugin,
                    "generationSpec", config.automation.generationSpecContract,
                    "feedback", config.automation.feedbackContract,
                    "interfaceDiscovery", config.automation.interfaceDiscoveryContract,
                    "scaffoldGenerator", config.automation.scaffoldGenerator,
                    "protectModifiedScaffolds", config.automation.protectModifiedScaffolds));
            root.put("startedAt", startedAt.toString());
            root.put("source", Map.of("path", plan.sourcePath(), "sha256", ArtifactStore.sha256(sourceContent)));
            root.put("inputs", inputPaths);
            if (evidencePath != null && !evidencePath.isBlank()) root.put("evidencePath", evidencePath);
            root.put("stages", stages);
            root.put("status", "RUNNING");
            persist();
        }

        public String runId() { return runId; }

        public void passed(PipelineStage stage, String path, String content, QualityGateService.GateResult gate) throws IOException {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", stage.name());
            item.put("skill", stage.skillName());
            item.put("status", "PASSED");
            item.put("path", path);
            item.put("sha256", ArtifactStore.sha256(ArtifactStore.clean(content)));
            item.put("qualityGate", Map.of("passed", gate.passed(), "errors", gate.errors()));
            item.put("completedAt", Instant.now().toString());
            stages.add(item);
            persist();
        }

        public void scaffold(String protocol, String selectorKind, String selectorValue,
                             String apiCode, String manifestPath, String executableizationPlanPath,
                             List<String> generatedFiles) throws IOException {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", "GENERATE_API_SCAFFOLD");
            item.put("status", "PASSED");
            item.put("protocol", protocol);
            item.put("selector", Map.of("kind", selectorKind, "value", selectorValue));
            item.put("apiCode", apiCode);
            item.put("manifestPath", manifestPath);
            item.put("executableizationPlanPath", executableizationPlanPath);
            item.put("generatedFiles", generatedFiles);
            item.put("completedAt", Instant.now().toString());
            stages.add(item);
            persist();
        }

        public String reject(PipelineStage stage, String content, QualityGateService.GateResult gate) throws IOException {
            String rejectedPath = config.paths.rejected + "/" + runId + "/" + stage.outputFile();
            store.save(rejectedPath, content);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", stage.name());
            item.put("skill", stage.skillName());
            item.put("status", "REJECTED");
            item.put("candidatePath", rejectedPath);
            item.put("qualityGate", Map.of("passed", false, "errors", gate.errors()));
            item.put("completedAt", Instant.now().toString());
            stages.add(item);
            root.put("status", "FAILED_GATE");
            persist();
            return rejectedPath;
        }

        public void failed(PipelineStage stage, Exception exception) {
            try {
                stages.add(Map.of(
                        "stage", stage.name(),
                        "status", "FAILED",
                        "message", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                        "completedAt", Instant.now().toString()));
                root.put("status", "FAILED");
                persist();
            } catch (IOException ignored) {
                // Original failure remains primary; UI logging still captures it.
            }
        }

        public void complete() throws IOException {
            root.put("status", "COMPLETED");
            root.put("completedAt", Instant.now().toString());
            root.put("durationMillis", java.time.Duration.between(startedAt, Instant.now()).toMillis());
            persist();
        }

        private void persist() throws IOException {
            String json = gson.toJson(root);
            store.save(plan.manifestPath(), json);
            store.save(config.paths.runs + "/" + runId + ".json", json);
        }
    }
}
