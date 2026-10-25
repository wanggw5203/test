# AI Test Flow IDEA Plugin 1.6.1

首次使用与三个工程的完整操作见 [完整操作手册](../../docs/使用与学习/三个工程完整操作手册.md)，框架学习见 [分层模块详解](../../docs/使用与学习/自动化框架分层模块详解.md)。

1.6.1 同步初始化工具 1.0.1：local/test 环境默认开启框架的脱敏请求/响应诊断日志，pre 环境关闭正文，控制台和按 PID 隔离的 JSONL 文件均可用。HTTP/Dubbo 记录逻辑位于自动化框架，不在测试类里复制打印；需使用包含 ProtocolCallLog 的最新框架依赖。本机 1.1.0-SNAPSHOT 已更新安装。

1.6.0 新增“创建自动化项目（HTTP / Dubbo / SDK）”。独立初始化工具已实现，不再要求用户另找初始化工程：选择类型即可生成框架依赖、main/test、环境配置、启动、报告和 YAML/JSON 初始化验证用例。规则位于独立模块 automation-project-initializer，插件仅调用；接口资产生成器继续不改写项目基础设施。见 [初始化工具说明](../automation-project-initializer/README.md)。

1.5.0 按职责拆分：框架依赖和项目初始化由其他工程负责，插件不生成 POM、不修改依赖、不生成公共启动与环境配置。插件读取现有自动化项目，main 下生成接口调用与业务造数/清理扩展，test 下生成 BaseTest/TestNG 测试方法和 YAML/JSON5 数据。生成前检查项目依赖声明，核对上游 caseId；重复生成还会比较实际输出，避免误复用旧版骨架。项目约定与迁移说明见 [插件边界与验收指南](PLUGIN-ASSET-CONTRACT.md)。

历史版本 1.4.0 首次对齐 BaseTest/DataDriveUtil，但当时的独立验证 POM 和全部代码放 test 的结构已由 1.5.0 替代。当前版本以 main/test 分层和外部初始化工程为准。

1.3.3 修复重复生成的误拦截：规格摘要按 YAML 内容计算，不受字段顺序、注释和换行影响；旧版清单通过预生成结果与现有文件逐项比较后安全复用，人工修改仍受保护。黄金集输出顺序固定。关闭黄金集后的模型网关调用继续保留，并已通过本地 HTTP 模拟网关覆盖全部生成阶段。

1.3 使用企业质量工作台界面：顶部统一操作区、项目资产编辑区、六阶段流程面板、集中配置和运行状态。窄工具窗口自动收起侧栏，通过“流程与配置”切换全宽面板；深浅主题沿用 IDEA 调色板。模型设置按“运行模式 / 企业模型网关”分组。

默认模型响应已改为固定黄金集 `order-create-v1`：无需配置模型或 API Key，即可验证 PRD → TAD → 用例 → HTTP/Dubbo 脚手架 → 反馈的资产流程。打开插件后点击“载入黄金集 Demo”，再运行完整链路。完整操作及预期产物见 [黄金集演练指南](GOLDEN-DEMO-GUIDE.md)。

面向团队测试左移的项目内资产工作流：

```text
PRD / Word / PDF / HTML / 原型截图
  → TAD / TRD / STD
  → 功能测试用例 YAML
  → 接口 generation-spec v2 + HTTP / Dubbo TestNG 脚手架 + Web ATC
  → 执行反馈
  → Asset Manifest + Run Record
```

## 企业能力

- Git 是唯一业务事实来源；插件不保存需求、用例或测试结论到个人目录。
- 项目外的 Word、PDF、HTML、Markdown 和原型会先按内容哈希登记到 `quality-assets/inputs/`，再进入分析链路。
- 手工输入和编辑器选区会先固化为输入文件；接口、代码、页面和执行证据会登记到 `quality-assets/evidence/`。
- 支持单需求完整链路；接口脚手架按单需求、单接口生成，逐文件指定接口后再进入下一条流程。
- Markdown PRD 可原位更新；其他格式保留原件并生成结构化 TAD。
- 六套 Skills 随插件发布，团队项目不依赖成员机器上的本地 Skills。
- 接口脚手架生成器源码随插件一起编译和发布，不依赖成员机器上的外置 JAR。
- `generation-spec.yaml` v2 支持 HTTP 与 Dubbo 目标、上游 `caseId` 追溯、接口选择器、BO/VO 等类型证据和生成 Profile。
- 可在项目内生成 Java/TestNG 测试类、用例套件 YAML、分环境请求/期望 JSON5、可执行化计划和带 SHA-256 的 scaffold manifest。
- HTTP 使用 `basetest-http` Profile；Dubbo 使用 `basetest-dubbo` Profile。插件不接受 `generic`；旧 generic 仅保留在独立生成器中兼容历史调用。
- 统一资产目录和稳定 `assetId`，成员在不同电脑上对同一路径会得到相同资产位置。
- 每个阶段执行质量门禁：TAD 必需章节、用例字段与唯一 `caseId`、自动化资产追溯字段。
- 门禁失败不会覆盖正式资产，候选文件进入 `.ai-test-flow/rejected/`。
- 生成开始时记录目标版本；生成期间若被其他人或进程修改，会停止覆盖。
- 每个需求维护最新 Manifest，每次执行保留独立 Run Record，记录来源摘要、模型、Skill、产物摘要和状态。
- API Key 只保存在 IDEA Password Safe；项目配置、产物和审计记录通过 Git 管理。
- 支持 OpenAI Chat Completions 兼容的公司模型网关；无 Key 时可使用演练模式验证流程。

## 1.2 接口自动化闭环

```text
PRD / TAD
  → 可追溯 TestCases YAML
  → generation-spec v2
  → 内置确定性生成器
  → Java/TestNG + suite YAML + request/expect JSON5
  → executableization-plan + scaffold-manifest
  → Git 评审 / CI 或测试框架执行
  → 真实日志作为 evidence 输入
  → feedback.yaml
```

插件只处理项目中的输入和产物，不在个人目录维护隐藏资产，也不会自动连接环境或执行真实测试。生成出的源码、数据和清单都位于当前 Git 项目，需与需求、TAD 和用例一起评审提交。

重复运行时，插件先校验已有文件摘要；规格内容或预生成结果与现有产物一致时直接复用，不覆盖文件。实际生成内容变化或人工修改时停止覆盖，提示通过 Git 评审并选择新的输出目录或接口版本。

插件已定义接口发现 SPI 和手工输入通道，用于后续按接口编码、HTTP 路径或 Java/Dubbo 方法追踪项目调用链并抽取 BO、VO、DTO 等资产。1.2 版本尚未提供真实 PSI/UAST、OpenAPI 或 Dubbo 元数据 Provider；因此当前发现结果必须来自用户选择和项目证据，不能把模型推测当成代码事实。

## 首次使用

1. 安装 ZIP 后打开知识库项目和右侧 `AI Test Flow`。
2. 点击“初始化企业工作区”，把生成的 `.ai-test-flow/workspace.json`、说明和目录提交 Git。
3. 在 `Settings → Tools → AI Test Flow` 配置公司模型网关、模型和个人 API Key；试用时可保留演练模式。
4. 选择一个需求，并在接口目标区明确填写 HTTP 方法路径（例如 `GET /api/orders`）或 Dubbo 方法签名（例如 `com.demo.OrderService#query(java.util.Map)`），运行完整链路。
5. 在接口目标区选择 HTTP 或 Dubbo，并填写接口选择器；生成 `generation-spec v2` 后运行脚手架生成。
6. 提交前评审 TAD、用例、Java/TestNG、YAML、JSON5、可执行化计划以及 Manifest。

默认目录：

```text
.ai-test-flow/
├── workspace.json
├── manifests/<assetId>.manifest.json
├── runs/<runId>.json
└── rejected/<runId>/...
quality-assets/
├── inputs/
├── evidence/
├── analysis/
├── testcases/
└── automation/
.api-test-generator/
└── <apiCode>/
    ├── executableization-plan.yaml
    └── scaffold-manifest.yaml
src/test/
├── java/.../<GeneratedTests>.java
└── resources/.../*.yaml / *.json5
```

`workspace.json` 只保存团队规范，不保存密钥。路径和门禁策略可通过 Git 统一修改。Markdown 的默认 `tadWriteMode` 为 `IN_PLACE_MARKDOWN`。

## 构建与安装

```powershell
.\gradlew.bat clean test buildPlugin
```

安装包：`build/distributions/ai-test-flow-idea-plugin-1.3.3.zip`。

在 IDEA 的 `Settings → Plugins → Install Plugin from Disk` 选择 ZIP。升级后需要重启 IDEA，旧插件实例不会自动热替换。

## 100 人团队部署边界

插件采用“每位成员本地执行 + 公司模型网关 + Git 评审合并”的分布式模式，本身不要求共享插件服务器。插件是无状态能力层：启动后从仓库重建上下文，输入、证据、产物、反馈和追溯关系全部来自项目文件。100 人团队应由平台侧提供统一模型网关的鉴权、限流、预算和审计；插件负责项目内资产治理、个人密钥隔离、批处理、质量门禁和冲突保护。详细说明见 [ENTERPRISE-ARCHITECTURE.md](ENTERPRISE-ARCHITECTURE.md)。

内置规则位于 `src/main/resources/builtin-skills/`，确定性接口生成器源码由插件构建直接编译进发布包。规则和生成器变更都应随插件版本统一发布，避免 100 名成员得到不同生成口径。
