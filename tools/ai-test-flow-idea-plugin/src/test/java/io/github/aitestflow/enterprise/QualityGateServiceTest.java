package io.github.aitestflow.enterprise;

import io.github.aitestflow.model.PipelineStage;
import io.github.aitestflow.service.DemoGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateServiceTest {
    private final QualityGateService gates = new QualityGateService();
    private final WorkspaceConfig config = new WorkspaceConfig();

    @Test
    void acceptsEveryDemoAssetType() {
        String prd = "# 优惠券\n\n- 用户每天只能领取一次优惠券";
        String tad = DemoGenerator.generate(PipelineStage.REQUIREMENT_TO_TAD, prd);
        String cases = DemoGenerator.generate(PipelineStage.TAD_TO_TEST_CASES, tad);
        assertTrue(gates.validate(PipelineStage.REQUIREMENT_TO_TAD, tad, config).passed());
        assertTrue(gates.validate(PipelineStage.TAD_TO_TEST_CASES, cases, config).passed());
        assertTrue(gates.validate(PipelineStage.TEST_CASES_TO_API_SPEC,
                DemoGenerator.generate(PipelineStage.TEST_CASES_TO_API_SPEC, cases,
                        "{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/demo\"}"), config).passed());
        assertTrue(gates.validate(PipelineStage.TEST_CASES_TO_WEB_ATC, DemoGenerator.generate(PipelineStage.TEST_CASES_TO_WEB_ATC, cases), config).passed());
        assertTrue(gates.validate(PipelineStage.FEEDBACK, DemoGenerator.generate(PipelineStage.FEEDBACK, ""), config).passed());
    }

    @Test
    void rejectsDuplicateCaseIds() {
        String yaml = "TestCases:\n  - caseId: A\n    priority: P1\n    steps: []\n    expected: ok\n  - caseId: A\n";
        assertFalse(gates.validate(PipelineStage.TAD_TO_TEST_CASES, yaml, config).passed());
    }

    @Test
    void rejectsModelChangingTheUserInterfaceSelector() {
        String cases = "TestCases:\n  - caseId: CASE-001\n";
        String spec = DemoGenerator.generate(PipelineStage.TEST_CASES_TO_API_SPEC, cases,
                "{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/orders\"}");
        QualityGateService.InterfaceTarget expected =
                new QualityGateService.InterfaceTarget("HTTP", "HTTP_PATH", "POST /api/orders");

        assertFalse(gates.validate(PipelineStage.TEST_CASES_TO_API_SPEC, spec, config, expected).passed());
    }
}
