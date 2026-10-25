# AI Test Flow 企业架构与团队运行规范

这里的 Git 指用户项目的版本管理，不要求 GitHub 或其他托管平台。插件是工具；输入资产和生成资产存放在当前项目，由团队自行提交、评审和合并。

## 1. 目标

1. 让 PRD、测试分析、功能用例、自动化输入和反馈成为同一个 Git 仓库中的可评审资产。
2. 让 100 人团队使用相同 Skills、目录、门禁和追溯协议。
3. 防止 AI 空结果、格式缺失和生成期间并发修改直接污染正式资产。
4. 不把个人 API Key、Word/PDF 原件或未经门禁的候选结果误当作正式结论。
5. 插件保持无状态：关闭或换电脑后，仅依靠 Git 仓库即可恢复完整需求与测试上下文。

## 2. 运行拓扑

```text
开发/测试成员的 IDEA 插件
        │ 个人凭据（Password Safe）
        ▼
公司模型网关（SSO/API Key、限流、预算、模型路由、审计）
        │
        ▼
项目工作区（资产、Manifest、Run Record）── Git PR/MR ── 主分支
```

插件不承担公司级账号系统。团队并发由公司模型网关承接，代码协作由 Git 分支和评审承接；插件在单个工作区内提供批量队列、目标版本校验和写入保护。

## 3. 全资产定义

| 资产 | 用途 | 默认位置 |
|---|---|---|
| 来源材料 | 需求事实来源 | 项目原路径 |
| 登记输入 | 项目外文档、原型和手工输入的不可变快照 | `quality-assets/inputs` |
| 补充证据 | 接口、代码、页面、评审与执行证据 | `quality-assets/evidence` |
| TAD/TRD/STD | 结构化业务与系统测试分析 | Markdown 原位或 `quality-assets/analysis` |
| TestCases YAML | 人工与自动化共享用例 | `quality-assets/testcases` |
| generation-spec | 接口脚手架输入 | `quality-assets/automation` |
| Java/TestNG 脚手架 | HTTP/Dubbo 自动化测试源码 | `src/test/java`（可由规格配置） |
| 接口用例数据 | 套件 YAML、分环境请求与期望 JSON5 | `src/test/resources`（可由规格配置） |
| scaffold manifest | 生成文件、摘要、协议、Profile 和证据追溯 | `.api-test-generator/<apiCode>` |
| executableization plan | 易失数据、枚举与造数的二次加工计划 | `.api-test-generator/<apiCode>` |
| Web ATC | Web 自动化资产 | `quality-assets/automation` |
| feedback | 执行证据与失败分类 | `quality-assets/automation` |
| Asset Manifest | 最新资产关系与摘要 | `.ai-test-flow/manifests` |
| Run Record | 单次生成审计证据 | `.ai-test-flow/runs` |

## 4. 质量与并发策略

- 所有输入必须先成为项目文件；项目外文件按内容哈希复制，手工输入和证据按内容哈希固化。
- 已登记的 `inputs` 不会被派生 TAD 覆盖；项目原有 Markdown 仍可按团队配置原位结构化，并由 Git 保留历史。
- 正式写入前先执行结构门禁，失败产物只能进入 `rejected`。
- 生成开始时读取目标文件摘要，写入前再次比对；不一致即停止，要求成员重新载入最新版本。
- 每个 Run Record 保存输入摘要、模型/模式、插件版本、Skill、输出摘要、门禁结果和时间。
- 同一需求的并行变更通过 Git 分支合并；Manifest 冲突必须由需求负责人确认，不建议自动选择一侧。
- 演练模式产物只能验证流程，不应作为正式测试结论合并。

## 5. 100 人团队建议

- 插件版本由平台组统一分发；Skills 变更必须升级插件版本并保留变更记录。
- `workspace.json` 由质量架构组维护，业务团队通过 PR 提议修改。
- 为 `quality-assets/` 和 `.ai-test-flow/` 配置 CODEOWNERS，至少测试负责人和开发负责人评审。
- 模型网关按团队/项目设置并发、速率、日预算、敏感信息过滤和审计保留期。
- CI 校验 Manifest 中的 SHA-256、用例唯一 ID 和必需章节；插件门禁用于本地前置反馈，CI 是合并前最终门禁。
- 批量任务建议按业务域分批，单批上限由 `maxBatchSize` 控制，默认 100。
- Word、PDF、图片等大文件建议由仓库启用 Git LFS，但仍必须经过版本管理和评审。

## 6. 与自动化框架形成闭环

```text
需求输入 → 测试分析 → YAML 用例 → generation-spec v2 / Web ATC
   ↑                                      ↓
Git 版本与评审 ← Manifest / feedback ← Java/TestNG + YAML + JSON5
                                             ↓
                                  CI / 自动化框架真实执行证据
```

- 插件生成标准化 `generation-spec.yaml` v2 和 Web ATC，作为 TestNG/自动化框架的 Git 输入资产。
- 接口脚手架生成器源码直接编译进插件发布包，不依赖成员机器上的本地 JAR。插件根据 v2 规格确定性生成 HTTP 或 Dubbo 的 Java/TestNG 测试类、suite YAML、请求/期望 JSON5、可执行化计划和 scaffold manifest。
- `basetest-http` Profile 面向 HTTP 路径与方法，`basetest-dubbo` Profile 面向接口类、方法与参数类型；`generic` Profile 保留既有自由模板兼容能力。
- 自动化框架或 CI 负责依赖解析、编译和真实执行，并把结果、日志或覆盖率写回项目证据目录。插件自身不连接真实环境，也不把“生成成功”冒充“测试通过”。
- 插件再读取这些证据生成 `feedback.yaml`，回溯到需求、规则和 `caseId`。
- `workspace.json` 的 `automation.executeFromPlugin` 默认是 `false`：插件不私自执行框架，不维护隐藏运行状态，只处理输入和产物。

### 6.1 Git 与覆盖保护

- `generation-spec`、Java、YAML、JSON5、executableization plan 和 manifest 全部写在当前项目内，由 Git 统一管理。
- 插件生成脚手架时禁止覆盖已有文件，重新生成应使用新的输出目录或接口版本，并通过 Git 评审合并。
- 已有目标文件被人工或二次加工修改后，manifest 校验会报告 `Modified`；这是需要评审的证据，插件会停止覆盖。
- `Missing` 或 `Malformed` 必须先修复；插件当前不提供强制覆盖已修改产物的按钮，应通过 Git 评审后人工处理再重新生成。
- 生成器不删除旧文件、不修改 Maven/Gradle 配置，也不执行测试命令。

### 6.2 接口发现扩展边界

1. 插件已预留接口发现 SPI，可表达 HTTP/Dubbo 协议、`API_CODE`、`HTTP_PATH`、`JAVA_SYMBOL`、`DUBBO_METHOD` 等选择方式，并承载端点、调用链、BO/VO/DTO 类型和源码证据。
2. 当前版本只提供手工输入/项目证据通道；真实 PSI/UAST 调用链分析、OpenAPI 扫描、Dubbo 注册与元数据 Provider 尚未实现。
3. 在 Provider 未接入前，模型不能自行宣称已发现真实链路。缺少方法、路径、接口类、参数类型或源码位置时，规格应标记待确认或停止脚手架生成。
4. 后续 Provider 只负责读取项目并返回结构化发现结果；生成器仍只消费 `generation-spec v2`，从而保持发现、推理与确定性产物生成解耦。

## 7. 当前边界

1. 已完成项目内单需求资产生成、质量门禁、冲突保护、运行追溯、个人密钥隔离，以及 HTTP/Dubbo 接口脚手架的内置确定性生成。完整链路暂按单需求执行，避免多需求共用一个接口而相互覆盖。
2. 已预留接口发现 SPI，但尚未实现真实 PSI/UAST、OpenAPI、Dubbo Provider 和跨模块调用链抽取；当前 BO/VO/DTO 与端点信息必须来自手工输入或已有项目证据。
3. 公司 SSO、集中预算、跨仓库检索、CI 状态回写和真实自动化执行器属于服务端/流水线能力，需要接入企业现有平台；插件已通过兼容模型网关和结构化资产为这些集成保留边界。
4. 插件不执行真实测试；`feedback` 只有在输入真实执行日志后才代表运行结论，无证据时 Skill 必须输出 `blocked/unknown`。
