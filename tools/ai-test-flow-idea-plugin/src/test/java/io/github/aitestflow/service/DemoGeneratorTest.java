package io.github.aitestflow.service;

import io.github.aitestflow.model.PipelineStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoGeneratorTest {
    @Test
    void createsTraceableDemoArtifactsWithoutModel() {
        String prd = "# 优惠券领取\n每个用户最多领取一张。\n库存不足时提示优惠券已领完。";
        String tad = DemoGenerator.generate(PipelineStage.REQUIREMENT_TO_TAD, prd);
        String cases = DemoGenerator.generate(PipelineStage.TAD_TO_TEST_CASES, tad);

        assertTrue(tad.contains("BR-001"));
        assertTrue(tad.contains("演练模式产物"));
        assertTrue(cases.contains("TestCases:"));
        assertTrue(cases.contains("DEMO-001"));
    }

    @Test
    void createsProtocolSpecificV2SpecsFromInterfaceEvidence() {
        String cases = "TestCases:\n  - caseId: CASE-001\n";
        String httpEvidence = "{\"protocol\":\"HTTP\",\"selectorKind\":\"HTTP_PATH\",\"value\":\"GET /api/orders\"}";
        String dubboEvidence = "{\"protocol\":\"DUBBO\",\"selectorKind\":\"DUBBO_METHOD\",\"value\":\"com.demo.OrderService#query(java.util.Map)\"}";

        String http = DemoGenerator.generate(PipelineStage.TEST_CASES_TO_API_SPEC, cases, httpEvidence);
        String dubbo = DemoGenerator.generate(PipelineStage.TEST_CASES_TO_API_SPEC, cases, dubboEvidence);

        assertTrue(http.contains("schemaVersion: 2"));
        assertTrue(http.contains("protocol: HTTP"));
        assertTrue(http.contains("method: \"GET\""));
        assertTrue(http.contains("profile: basetest-http"));
        assertTrue(http.contains("sourceCaseId: CASE-001"));
        assertTrue(dubbo.contains("protocol: DUBBO"));
        assertTrue(dubbo.contains("interfaceName: \"com.demo.OrderService\""));
        assertTrue(dubbo.contains("dubboMethod: \"query\""));
        assertTrue(dubbo.contains("profile: basetest-dubbo"));
    }
}
