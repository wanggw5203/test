package io.github.aitestflow.enterprise;

import java.util.List;

public final class WorkspaceConfig {
    public int schemaVersion = 1;
    public String workspaceId = "quality-knowledge-base";
    public String team = "quality-engineering";
    public int expectedTeamSize = 100;
    public int maxBatchSize = 100;
    public String tadWriteMode = "IN_PLACE_MARKDOWN";
    public Paths paths = new Paths();
    public Gates gates = new Gates();
    public Automation automation = new Automation();
    public List<String> requiredReviewers = List.of("test-owner", "development-owner");

    public static final class Paths {
        public String inputs = "quality-assets/inputs";
        public String evidence = "quality-assets/evidence";
        public String analysis = "quality-assets/analysis";
        public String testcases = "quality-assets/testcases";
        public String automation = "quality-assets/automation";
        public String manifests = ".ai-test-flow/manifests";
        public String runs = ".ai-test-flow/runs";
        public String rejected = ".ai-test-flow/rejected";
    }

    public static final class Gates {
        public boolean rejectEmptyOutput = true;
        public boolean requireTadSections = true;
        public boolean requireUniqueCaseIds = true;
        public boolean requireTraceability = true;
    }

    public static final class Automation {
        public String framework = "TestNG";
        public String handoffMode = "GIT_ASSET";
        public boolean executeFromPlugin = false;
        public String generationSpecContract = "generation-spec.yaml";
        public String feedbackContract = "feedback.yaml";
        public String interfaceDiscoveryContract = "InterfaceDiscoveryProvider";
        public String scaffoldGenerator = "BUNDLED";
        public String httpProfile = "basetest-http";
        public String dubboProfile = "basetest-dubbo";
        public boolean protectModifiedScaffolds = true;
    }
}
