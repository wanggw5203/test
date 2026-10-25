---
name: api-test-automation-workflow
description: "把 TAD/TRD/STD 生成的接口或服务端 YAML 用例转换为 generation-spec v2，并通过插件内置生成器生成 HTTP/Dubbo Java/TestNG、YAML、JSON5、执行化计划和清单；再依据外部框架或 CI 的真实执行证据形成上游反馈。用户要求生成接口自动化框架代码、把接口 YAML 转成脚手架、加工接口测试用例，或建立接口自动化闭环时使用。"
---

# 接口自动化生成闭环

把上游接口测试意图稳定地落成“接口证据 + generation-spec v2 + 框架脚手架 + 可执行数据 + 清单 + 上游反馈”。开始前必须阅读 [生成器规格](references/generator-spec.md) 和 [反馈契约](references/api-feedback-contract.md)。生成器源码已经编译进插件，不读取 `<skill-dir>/assets`，也不要求成员机器安装本地 JAR。

## 确定执行范围

- 当前插件只处理已经初始化的自动化模块。框架是公共依赖，项目目录、依赖引入、公共 bootstrap 与环境配置由其他工程完成，不生成 POM 或改写公共运行时。
- 业务 API 与造数/清理扩展进入 main；TestNG 测试方法、用例 YAML、JSON5 数据进入 test。生成测试类不得用空的 AutomationRuntime 覆盖项目已安装的身份、报告和平台适配能力。

- 插件内的完整链路到生成 Git 资产、清单和可执行化计划为止；插件不编译、不连接真实环境、不执行真实测试。
- 只有提供来自 CI 或自动化框架的真实日志时，才进行运行诊断和反馈；不得把脚手架生成成功写成测试通过。
- 只要求生成框架时，生成脚手架并完成清单校验，不擅自连接真实环境。
- 已有 `apicode_xxx` 脚手架时，不重复生成；直接进入可执行化加工。
- 输入用例的 `tags` 为 `接口` 或 `服务端` 时进入本流程；`前端` 用例交给 `$test-yaml-to-web-atc`。
- 接口契约、鉴权方式或目标框架证据不足时，只生成明确可证实的部分，并记录待确认项。
- 用户要求新建隔离项目、初始化工程或切换其他框架时，说明这属于外部初始化工程职责，不擅自启用便携模式或改成 generic 自由模板。

## 第一阶段：确定接口并读取框架证据

先确定 `protocol` 和选择器：HTTP 可按接口编码、HTTP 路径或 Java Symbol；Dubbo 可按接口编码、Java Symbol 或 Dubbo 方法。插件已预留接口发现 SPI，但当前没有真实 PSI/UAST、OpenAPI 或 Dubbo 元数据 Provider。当前版本只能使用用户输入和仓库中可引用的证据，不能宣称模型推测的调用链、BO、VO 或 DTO 是已发现事实。

先检查目标仓库，不凭通用经验虚构类名、包名或调用方式。至少读取：

1. `pom.xml`、父 POM 和测试插件配置。
2. 同模块最近的已跑通 TestNG 测试类、数据驱动 YAML、请求与期望 JSON5。
3. API 封装类、RO/DTO、鉴权注解、`BaseTest`、`DataDriveUtil` 和数据准备器。
4. 仓库内已有生成规则、数据策略、诊断规则和未提交改动。

把已跑通样例视为生成形状的最高优先级证据。工作树有用户改动时必须保留，不得回退或覆盖。

当前插件仅支持 basetest-http、basetest-dubbo 两种框架 Profile；没有真实源码证据时不生成猜测的 RO、VO/DTO 类型。独立生成器的旧 generic 兼容能力不属于插件支持范围。

真实接口发现 Provider 后续接入时，只把端点、调用链、类型和源码位置写入结构化发现结果；生成阶段仍只消费 `generation-spec v2`。证据不足时停止并列出待确认项，不补造类名、方法签名或字段。

## 第二阶段：形成生成规格

从接口 YAML、TAD/STD 和本地框架证据生成一份 `generation-spec.yaml`。HTTP/Dubbo 新规格必须使用 `schemaVersion: 2`，并包含 `sourceCaseIds`、`target.protocol`、`selector`、协议对应的 `endpoint`、`generator.profile` 和 `cases[].sourceCaseId`。完整字段见 [生成器规格](references/generator-spec.md)。

映射时遵守：

- `caseId/name/thought` 保持上游追溯语义；不得为了适配框架改写业务目标。
- `before` 拆成环境基础数据、运行时造数、鉴权用户和业务前置状态。
- `steps.step` 映射接口调用；`steps.expected` 映射期望 JSON5，不能弱化断言。
- `query` 用于只依赖稳定查询条件的接口；需要先创建、推进或绑定业务状态时使用 `data-preparation`。
- `java.*` 必须来自目标仓库真实类与黄金样例。没有证据时留待确认，不猜测 import、方法签名或响应类型。
- 每个易失字段都写入 `fieldHints`，供二次加工阶段分类处理。
- HTTP 使用 `generator.profile: basetest-http`，并提供 `endpoint.method/path`；Dubbo 使用 `generator.profile: basetest-dubbo`，并提供 `endpoint.interfaceName/dubboMethod/parameterTypes`。
- `types` 与 `evidence` 只记录已验证的 BO/VO/DTO 和源码证据；没有发现 Provider 或源码依据时留空或待确认，禁止伪造。

## 第三阶段：生成脚手架

通过插件的“生成 TestNG 接口脚手架”能力调用内置生成器。插件禁止覆盖已有文件；重新生成应选择新的输出目录或接口版本，并通过 Git 评审合并。所有输出必须位于当前项目的配置目录内，不得写入用户目录或插件缓存。

生成器输出 HTTP 或 Dubbo Java TestNG 测试类、`testDataSuite` YAML、各环境请求/期望 JSON5、可执行化计划和带 SHA-256 的清单。生成后立即校验文件存在性、清单结构和摘要。

当前内置生成器只渲染测试类和数据资源，不生成业务实现类。便携模式还必须由 Skill 在同一目标项目补齐：

- 请求RO，保留已证实的字段名、类型和序列化映射；
- 响应VO/DTO，包括响应包装及其泛型数据类型；
- API客户端或接口定义，保留HTTP方法与路径，基础地址和鉴权通过通用配置注入；
- 仅包含JDK和公开依赖的构建文件与最小数据加载/断言支持。

这些契约文件必须进入最终清单或单独的便携产物清单，并接受编译与私有依赖扫描。

严格校验失败时停止二次加工，先修复规格或生成器问题。插件不提供强制覆盖已修改产物的入口；必须先通过 Git 评审处理差异，再重新生成。

## 第四阶段：二次可执行化

读取 `executableization-plan.yaml`，并使用 `$make-api-tests-executable` 加工已生成脚手架。框架已生成的业务覆盖默认正确，重点处理数据与运行条件：

- 固定值和明确默认值直接保留。
- 业务枚举只按接口文档、字典、邻近黄金脚本或代码常量转换，不凭字段名猜值。
- 环境基础数据从配置或稳定查询获取。
- 运行时业务数据优先使用 `__DATA_REF:alias.output__`，通过数据准备器创建并回填。
- 唯一字段在运行时生成；反向用例中的非法值、空值和越界值原样保留。
- 前置数据失败应阻塞或跳过目标调用，不把造数失败伪装成接口断言失败。

业务造数放在 databuilder 或项目已有数据准备层；测试类只保留编排、调用和断言。禁止删除用例、缩小覆盖或把具体期望改成宽松成功判断。

二次加工后使用非严格摘要校验。`Modified` 是人工或执行化加工的预期证据，必须保留并评审；`Missing` 和 `Malformed` 必须为 0。再次生成不得静默覆盖 `Modified` 文件。

## 第五阶段：移交编译与运行

插件不执行真实测试。把生成资产提交 Git 评审后，由开发者、自动化框架或 CI 按目标仓库既有命令做测试编译，再运行最小目标单例。建议顺序：

1. `mvn -DskipTests test-compile`
2. `mvn -Dtest=<GeneratedTests> test`
3. 仓库已有的环境参数、TestNG suite 或启动脚本

不要一开始运行全量测试。真实环境需要账号、网络或数据权限时，由执行方记录阻塞证据，不伪造通过结果。只有真实日志、报告或覆盖率文件重新登记为项目 evidence 后，插件才能据此生成反馈。

## 第六阶段：诊断修复

收到编译或运行证据后，诊断流程应：

1. 先按编译、启动、鉴权、网络、数据准备、业务响应、字段对比分类。
2. 找到第一条决定性证据，再做最小修复。
3. 每次只修一个根因并复跑同一单例。
4. 产品缺陷和真实契约不符不得通过修改期望来“修绿”。
5. 环境问题不得写成业务规则。

## 第七阶段：回写闭环

根据 [反馈契约](references/api-feedback-contract.md) 在工作区自动化资产目录生成 `<assetId>.feedback.yaml`，并通过 `apiCode` 和 manifest 关联脚手架，按责任边界回写：

- 框架模板、路径或渲染错误：修生成规格或生成器。
- 易失数据、枚举或造数错误：修 `fieldHints`、数据策略或 databuilder。
- 业务规则冲突：确认后按 TAD/STD -> 接口 YAML -> 脚手架传播。
- 产品缺陷、环境或权限问题：保留证据并标记阻塞，不改测试期望。
- 稳定跑通的单例：登记为接口黄金样例，供后续生成规格校准。

始终维护：

```text
需求规则 -> TAD/STD -> 接口 YAML caseId -> generation-spec
-> scaffold-manifest -> Java/YAML/JSON5 -> runId -> feedback/golden sample
```

## 完成门禁

- 生成规格可解析，且所有 Java 框架元素都有本地证据。
- 首次严格清单校验通过。
- 二次加工后无缺失或畸形产物，反向场景未被破坏。
- 脚手架已移交 Git/CI；如有真实执行证据，则明确记录编译与目标单例结果。
- 未通过项有明确根因、证据、责任边界和下一步。
- 上游反馈与黄金样例登记完成，且没有静默覆盖用户文件。
- 便携模式包含RO、VO/DTO、API客户端、测试代码和用例资源，且生成目录不存在来源公司的私有包名或框架引用。

## 汇报结果

说明生成规格、Java/YAML/JSON5、清单、可执行化计划和反馈文件的位置；列出生成与清单校验结果、`Modified` 文件、阻塞项、二次加工的数据类型，以及是否收到真实执行证据。没有执行证据时必须明确写“未执行”，不得只汇报“已生成”后暗示已通过。
