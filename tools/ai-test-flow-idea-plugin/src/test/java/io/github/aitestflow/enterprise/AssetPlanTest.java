package io.github.aitestflow.enterprise;

import io.github.aitestflow.model.PipelineStage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPlanTest {
    @Test
    void markdownStaysInPlaceAndOtherAssetsUseGovernedDirectories() {
        Path root = Path.of("C:/repo").toAbsolutePath().normalize();
        AssetPlan plan = AssetPlan.forSource(root, root.resolve("requirements/优惠券.md"), new WorkspaceConfig());
        assertEquals("requirements/优惠券.md", plan.tadPath());
        assertTrue(plan.testcasePath().startsWith("quality-assets/testcases/"));
        assertEquals(plan.apiSpecPath(), plan.pathFor(PipelineStage.TEST_CASES_TO_API_SPEC));
    }

    @Test
    void registeredInputIsNeverOverwrittenByDerivedTad() {
        Path root = Path.of("C:/repo").toAbsolutePath().normalize();
        AssetPlan plan = AssetPlan.forSource(root, root.resolve("quality-assets/inputs/需求-a1b2.md"), new WorkspaceConfig());
        assertEquals("quality-assets/inputs/需求-a1b2.md", plan.sourcePath());
        assertTrue(plan.tadPath().startsWith("quality-assets/analysis/"));
    }
}
