package io.github.aitestflow.model;

public enum PipelineStage {
    REQUIREMENT_TO_TAD(
            "需求 → TAD/STD",
            "requirement-to-tad",
            "01-tad-std.md",
            "将下面的 PRD 转换为结构化 TAD，包含 TRD 与 STD。只输出最终 Markdown 文档，不解释生成过程。"),
    TAD_TO_TEST_CASES(
            "TAD/STD → 测试用例",
            "tad-to-test-yaml",
            "02-testcases.yaml",
            "将下面的 TAD/TRD/STD 转换为可追溯的测试用例 YAML。只输出 YAML，不使用 Markdown 代码围栏。"),
    TEST_CASES_TO_WEB_ATC(
            "测试用例 → Web ATC",
            "test-yaml-to-web-atc",
            "03-web.atc.yaml",
            "将下面的功能测试用例 YAML 转换为 Web ATC。缺少真实页面或黄金脚本证据时必须标记待确认，不得猜测定位器。只输出 YAML。"),
    TEST_CASES_TO_API_SPEC(
            "测试用例 → 接口规格",
            "api-test-automation-workflow",
            "03-generation-spec.yaml",
            "根据下面的功能测试用例与接口发现证据，生成 api-test-scaffold-generator v2 可读取的 generation-spec.yaml。"
                    + "必须保留 sourceCaseIds 与每条 cases.sourceCaseId，target.protocol 只能是 HTTP 或 DUBBO，"
                    + "selector 必须原样采用用户输入，generator.profile 分别使用 basetest-http 或 basetest-dubbo。"
                    + "没有源码证据时 types/evidence 保持空或标记待补充，不得猜测 Java 类型、接口方法、调用链或业务枚举。只输出 YAML，不使用 Markdown 代码围栏。"),
    FEEDBACK(
            "执行证据 → 反馈报告",
            "close-atc-feedback-loop",
            "04-feedback.yaml",
            "根据下面的执行证据输出结构化反馈 YAML。未实际执行或证据不足时必须使用 blocked/unknown，不得写成 passed。只输出 YAML。" );

    private final String displayName;
    private final String skillName;
    private final String outputFile;
    private final String instruction;

    PipelineStage(String displayName, String skillName, String outputFile, String instruction) {
        this.displayName = displayName;
        this.skillName = skillName;
        this.outputFile = outputFile;
        this.instruction = instruction;
    }

    public String displayName() { return displayName; }
    public String skillName() { return skillName; }
    public String outputFile() { return outputFile; }
    public String instruction() { return instruction; }
}
