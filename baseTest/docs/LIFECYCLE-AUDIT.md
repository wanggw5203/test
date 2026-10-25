# 自动化执行闭环审计

审计日期：2026-10-15。适用版本：`1.1.0-SNAPSHOT`，尚未正式发布。

本文沿一次自动化运行的开始、执行、结束、交付检查实际代码。判断标准是：每个节点能定位输入、所有者、成功证据和失败去向；调用失败、超时或清理异常后，不能继续把结果解释为成功。本文记录已实现的控制及证据边界，不将本地回归等同于公司环境验收。

规范入口见 [工程规范](ENGINEERING-STANDARDS.md)，团队协作与准入职责见 [企业团队模式](../ENTERPRISE-TEAM-MODE.md)，较早的本地验收记录见 [验收记录](../ENTERPRISE-ACCEPTANCE.md)。本文不固定最新 Java 测试总数；每次运行应读取当次 `target/verification-summary.json`、各模块 Surefire 报告和 `target/verification-evidence.json`。

## 1. 一次运行的完整路径

```text
选择精确框架/BOM版本与环境
  → 校验环境、身份、目标资源与操作权限
  → 发现用例、解析元数据、验证完整计划并分片
  → 获取身份/资源租约，创建关联标识，准备数据并注册补偿
  → 调用 HTTP / Dubbo / SDK，或触发任务 / 消息
  → 等待并关联实际结果，断言业务状态和副作用
  → 逆序补偿、关闭资源、释放身份与上下文
  → 记录用例及配置失败，完成运行汇总
  → 校验报告、全分片计划、源码与制品证据
  → 归档 / 审核 / 发布精确版本 / 消费试点 / 升级或回滚
```

任一节点失败都必须保留失败证据。远程请求已经发出但结果未知时，先核对原请求的执行记录和业务副作用；不能通过再次发送、再次触发或忽略异常把它改成成功。

## 2. 各节点实现与失败闭环

表中的类可从模块 `src/main/java` 对应包直接定位；测试位于相同模块的 `src/test/java`。`core` 指 `base-test-toolkit`，`adapters` 指 `enterprise-adapters`。

| 节点 | 实现入口 | 成功与失败闭环 | 当前证据与边界 |
|---|---|---|---|
| 环境配置 | core `config.EnvironmentConfig`、`TestEnvironment`、`ConfigCatalog`、`TestClients`；`config/environments.yaml` | 解析环境及必要值，限制正数超时/容量，明确覆盖顺序；未知环境或未解析占位符失败。客户端按需初始化，由使用者关闭。 | `EnterpriseCapabilitiesTest`、`TestEnvironmentTest`、`ConfigCatalogTest`、`TestClientsTest`。dev/qa 配置模型可用；公司 URL、凭据和环境资源关系仍需验证。 |
| 执行准入 | core `enterprise.EnterpriseExecution`、`ExecutionPolicy`、`TargetPolicy` | 检查环境、团队、服务、破坏性操作；企业上下文内 `ApiClient` 在发请求前检查 HTTP origin。未登记目标默认拒绝。 | `EnterpriseModeTest`、`TargetPolicyTest`。HTTP 白名单不覆盖路径级权限，不能替代公司 IAM/网络 ACL，也不约束绕过框架的自建客户端。 |
| 用例发现与所有权 | core `enterprise.TestCaseMetadata`、`CaseDefinition`、`EnterprisePlan` | 规范注解声明稳定 ID、owner、team、service、level；元数据缺失、非法值、不同方法逻辑 ID 冲突或策略不符时失败。旧 `CaseInfo` 保留兼容并弃用；同一声明混用两个注解会失败。 | `TestCaseMetadataTest`、`EnterpriseModeTest`。正式业务用例使用显式方法 ID，避免 Java 方法重命名导致历史身份变化；owner 标识仍需映射公司人员/责任组目录。 |
| 计划与分片 | core `enterprise.Shard`、`EnterprisePlanManifest`；`ci/verify-shards.ps1` | 按稳定逻辑 ID 确定方法所属分片；校验先于级别过滤/分片。清单选中集合必须等于该分片应有集合；空集合、缺分片、重复执行、计划漂移与缺完成汇总均失败。 | `EnterprisePlanManifestTest`、`EnterpriseModeTest`、`ci/test-shard-gate.ps1`。DataProvider 同一方法的全部行位于同一分片；门禁要求每个逻辑 ID 有成功事件，不证明全部预期数据行已覆盖。 |
| 身份与上下文 | core `runtime.PortableTestNgListener`、`RuntimeRegistry`、`TraceContext`；`identity.IdentityContext`、`IdentityProvider`；`enterprise.EnterpriseContext` | 每次调用捕获 runtime，建立身份、追踪和企业作用域；清理分别尝试，前一个释放失败不跳过后一个。执行 ID 区分同名数据行，fixture key 包含 run/worker/环境/团队/服务/用例/调用。 | `LifecycleRegressionTest`、`RuntimeIsolationTest`、`EnterpriseModeTest`；含 100 线程隔离回归。不是 100 人真实协作或多节点容量验收。 |
| 配置阶段 | core `runtime.PortableTestNgListener.beforeInvocation/afterInvocation` | `@Before*`/`@After*` 也绑定并恢复所捕获 runtime 和 trace。配置失败记录 `phase=configuration`；teardown 修改 provider 后，失败仍送达原 publisher。 | `LifecycleClosureTest`、`ConfigurationReportTest`。配置方法未必属于一个逻辑用例，因此不会默认获取用例身份或绑定 EnterpriseContext；配置阶段须显式执行资源准入和账号授权。 |
| 独占资源 | core `lease.JdbcResourceLeaseProvider`、`ResourceLease`；`config/resource-leases.sql` | 数据库时间、条件更新、TTL、续期、过期接管、owner/fencing token 共同约束租约；旧持有者不能释放替代租约。获取失败不继续操作共享资源。 | `JdbcResourceLeaseTest` 验证 H2 竞争、续期、接管及旧 owner 保护。尚未验证实际数据库多节点故障；业务防护端必须实施 fencing 校验，租约失效不会撤销已发远程请求。 |
| 测试数据与数据库 | core `data.DataFactory`、`CaseContext`、`database.JdbcSession`、`runtime.CleanupStack` | 创建数据后立即登记补偿；JDBC 使用预编译参数、查询限制和事务，未提交变更关闭时回滚；事务初始化失败关闭已接管连接。需要跨进程可见时显式 commit 并额外登记删除补偿。 | `EnterpriseCapabilitiesTest`、`JdbcSessionTest`。回滚不撤销已提交数据或远端副作用；DataFactory 是接入 SPI，不会自动生成各业务域数据。 |
| HTTP | core `http.ApiClient`、`ApiResponse` | 请求超时、响应体大小限制、受控重定向与显式状态/JSON 断言；企业目标检查先于发送，调用日志只记录结构信息。 | `ApiClientTest` 和本地 `java-api-lab` 接口回归。鉴权练习服务及本地契约不证明公司网关、证书、SSO 或权限矩阵已验收。 |
| Dubbo | adapters `dubbo.DubboClient` | 显式服务/版本/分组配置，泛化调用、超时与资源关闭；异常交给测试失败链路，不隐式重试业务操作。 | adapters 的 Dubbo 回归验证本地直连。真实注册中心、路由、序列化组合、鉴权和跨版本兼容仍需环境矩阵。 |
| SDK | core `sdk.SdkInvoker`、`config.TestClients` | 有界线程与队列，传播 runtime/身份/trace/企业上下文；超时、中断、关闭和拒绝提交均失败并记录调用；日志也失败时保留业务异常为主异常。 | `EnterpriseCapabilitiesTest`、`SdkShutdownTest`、`SdkFailureReportingTest`。取消通过中断请求；忽略中断的 SDK 可能继续执行，不存在框架内安全强停承诺。 |
| 本地任务 | core `job.LocalJobService`、`JobAwait`、`JobExecution` | 区分排队、运行、成功、失败、取消和未知；关闭时未开始任务标为 CANCELLED，运行任务标为 UNKNOWN，等待器将未知结果判为失败并要求核对原任务。 | `LocalJobServiceTest`、`JobAwaitTest`。本地一次性调度不提供分布式 cron、任务治理或远程业务强停。 |
| XXL-JOB | adapters `xxljob.XxlJobService`、`XxlJobDialect`、`XxlJobSubmissionException` | 登录、触发表单、执行日志发现、按 execution ID 查询与终态等待分离；明确拒绝为 REJECTED，网络/响应/日志关联不确定为 OUTCOME_UNKNOWN。同一 adapter 随后阻止该 job 重触发；日志结果不按成功/失败被选择性丢弃。 | `XxlJobServiceTest` 使用本地管理端契约，覆盖 2.4.1/3.1.1 方言及未知结果；`XxlJobLiveIT` 为显式真实环境入口，未在本轮执行。阻止重触发只作用于当前 adapter，跨进程仍需专用 job、租约和业务幂等。 |
| 消息发送与证据等待 | adapters `kafka.KafkaPublisher`、`KafkaMessageProbe`、`KafkaDeliveryException`；core `message.MessageProbe` | 业务动作前 `ready` 完成分区与起始 offset 初始化；用关联 key/header 等待证据。发送确认与业务消费断言分开；发送超时/中断返回 OUTCOME_UNKNOWN，不取消或自动重发原消息。 | `KafkaAdaptersTest` 使用 Kafka Mock 客户端；`KafkaLiveIT` 为显式 broker 往返入口，本轮未执行。Kafka producer 幂等不等于业务端幂等，broker 确认不证明消费业务成功。 |
| 消费处理 | adapters `kafka.KafkaConsumerRunner`、`KafkaOffsetCommitException` | handler 失败回退到失败及未处理 offset；回退失败、缓冲溢出或提交结果不明后停用 runner，避免后续提交越过失败记录或重复执行业务副作用。成功处理只提交其分区的已处理 offset。 | `KafkaAdaptersTest` 覆盖失败回退、提交确认丢失、初始化、容量溢出和构造失败释放。真实 rebalance、网络故障、事务/死信及业务幂等需独立验证；重新创建 runner 前仍须核对已有副作用。 |
| 业务结果断言 | core `assertion.JsonDiff`、`JsonContract`、`Await`、`job.JobAwait`；业务域测试 | 同时检查协议结果、业务状态、数据库/消息/任务副作用和关联标识；有界等待终态，失败/取消/未知立即失败，不以固定 sleep 或仅 HTTP 200 作为完成证明。 | `JsonContractTest`、`AwaitTest`、`JobAwaitTest`、本地接口及团队示例。断言是否覆盖金额、权限、幂等与一致性由业务负责人评审，框架不自动推导业务正确性。 |
| 清理与关闭 | core `runtime.CleanupStack`、listener cleanup；各 `AutoCloseable` 客户端 | 补偿逆序执行，异常聚合并保留原始失败；mock、身份、企业上下文、runtime、trace 分别尝试释放。清理失败会使当前结果失败，不能因主体断言通过而隐去。 | `LifecycleClosureTest`、`LifecycleRegressionTest`、`JdbcSessionTest`、`SdkShutdownTest`。进程被杀或机器宕机后 finally 无法保证执行；尚无持久化补偿台账和自动遗留数据清理服务。 |
| 结果与汇总 | core `runtime.JsonLinesResultPublisher`、`CompositeResultPublisher`、`PortableTestNgListener` | 一次调用只完成一次结果记录，发布器绑定实际 runId，拒绝同 publisher 混 run；扇出和最终 complete 遇 Error 也继续其他 publisher。JSONL 采用元数据白名单，不写任意业务载荷或原始异常文本。 | `JsonLinesResultPublisherTest`、`LifecycleClosureTest`、`ConfigurationReportTest`。每进程/worker 独立文件；同步约束限同一 publisher 实例，不支持多进程共同追加同一文件。TestNG XML 仍可能含业务自行抛出的敏感文本，归档权限和脱敏需公司治理。 |

## 3. CI 如何判定这次运行真正结束

完整本地验收使用根目录 [verify-lifecycle.ps1](../verify-lifecycle.ps1)。聚焦开发测试可以直接运行 Maven；缩小选择的运行不作为该统一入口的完整验收。

```powershell
./verify-lifecycle.ps1
```

该入口执行以下闭环：

1. 先将证据状态写为 RUNNING，生成或接收安全 runId；检查工具。拒绝继承的测试筛选及验收绕过参数，显式固定 local、all、单分片及报告位置。
2. 执行 `clean verify`：编译、单元/本地契约测试、默认 Spotless 规范检查，启用 `supply-chain` 生成包括 test scope 的 CycloneDX 1.6 JSON/XML SBOM。
3. [verify-framework.ps1](../verify-framework.ps1) 校验四个测试模块有报告、有实际执行、无失败/错误/超额跳过；核对 XML 计数与 testcase 内容，拒绝陈旧报告与 DTD。
4. [verify-shards.ps1](../ci/verify-shards.ps1) 将团队示例计划与 JSONL 的 runId、分片、逻辑 ID 及完成 summary 关联，拒绝漏跑和假完成。
5. 执行报告、分片、证据及完整执行参数准入回归，当前分别有9、32、24、16个场景，共81个；参数准入只检查字符串，不设置进程变量或携带危险选项启动Maven。后续修改以脚本真实输出为准。
6. 记录源码指纹与每个制品的相对路径、长度、SHA-256；确认构建期间输入没有改变。证据要求四个模块的测试报告、三个主二进制、计划、JSONL、汇总及两份 SBOM，失败时状态写为 FAILED。
7. [verify-evidence.ps1](../ci/verify-evidence.ps1) 重新计算源码指纹和制品哈希，核对必需文件，拒绝缺模块、内容改变、不安全路径、ADS、越界与链接制品。复验失败会撤销 PASSED 状态。

源码指纹使用规范化相对路径和 ordinal 排序，覆盖根构建入口、各 Java 模块 POM/src、ci、config，以及存在时的 Maven wrapper/.mvn 文件。Git commit 和 dirty 状态额外记录；工作区有未提交改动时，以输入指纹说明本次实际内容。

| 证据文件 | 用途 | 不应从中推断的结论 |
|---|---|---|
| `target/verification-summary.json` | 当次四模块实际测试数及跳过数 | 不包含真实公司平台或全部数据行的验收承诺 |
| `enterprise-team-example/target/enterprise-plans/*.plan.json` | 完整/分片逻辑用例集合与 planHash | planHash 只覆盖逻辑 ID，不覆盖代码/BOM/数据版本 |
| `enterprise-team-example/target/team-results.jsonl` | 调用、用例、配置失败及运行 summary 的关联 | 传输成功不能代替业务结果，缺完成 summary 不能接受 |
| `target/testkit-sbom.json`、`.xml` | 本次依赖组件清单，包含测试依赖 | 生成 SBOM 不等于已完成漏洞扫描、许可证审批或制品签名 |
| `target/verification-evidence.json` | 本次阶段状态、源码指纹、制品长度和 SHA-256 | 无公司签名的哈希清单不提供发布授权、可信签名或可复现构建证明 |

[Jenkinsfile](../ci/Jenkinsfile) 作为公司 CI 的模板入口，应调用同一验收脚本并始终归档诊断和证据。模板文件存在、脚本在本机通过，不证明 Jenkins 服务端、runner 权限、分支保护或 JDK17 节点已经配置并验收。

## 4. 发布、业务消费和升级也属于闭环

| 阶段 | 已有实现 | 完成条件与待接入项 |
|---|---|---|
| 构建与版本组合 | `testkit-parent` 管理构建，`testkit-bom` 管理精确依赖组合；编译目标 Java 17，插件版本集中定义 | 正式版本固定 Git SHA、父 POM/BOM/核心/适配器版本及验证证据；本机 JDK 版本与 CI 目标版本分别记录。 |
| 发布准入 | `enterprise-publish` profile 要求 release/snapshot HTTPS 仓库 URL | 公司配置独立发布凭据、受保护分支、审核、漏洞与许可证门禁、签名和不可变 release 策略；本轮未向远程仓库 deploy。 |
| 外部项目消费 | 可继承父 POM 或只导入 BOM；框架版本不依赖业务自己的 `project.version` | 曾验证业务版本 9.9.9 两种消费模型解析到框架版本；需要再从实际私有仓库下载验证，不能只依赖维护者本机 install。 |
| 兼容与迁移 | `TestCaseMetadata` 为规范注解，旧 `CaseInfo` 保留兼容；XXL-JOB 方言为扩展契约 | 保持稳定逻辑 ID；记录公共 API、配置、报告 schema、hash/分片和适配器版本兼容性，先试点业务域再扩散。 |
| 升级与回滚 | 精确版本消费允许业务仓库独立升级 | 保存旧 BOM、旧制品和环境配置，回滚测试执行依赖；已执行的远端任务、消息或数据不会随框架版本回滚自动撤销，需业务补偿。 |

当前 `1.1.0-SNAPSHOT` 是开发版本。正式发布前应把最终验收、迁移说明和已验证的兼容矩阵写入 [CHANGELOG](../CHANGELOG.md)，通过公司仓库分发不可变 release。

## 5. 本轮已修复的闭环缺口

| 原问题 | 修复后的行为 | 定位回归 |
|---|---|---|
| 配置阶段客户端使用全局 runtime；teardown 更换 provider 可能把配置失败送错位置 | 配置阶段建立 runtime/trace 作用域，失败发布使用实际捕获 runtime | `LifecycleClosureTest.configurationCallsUseCapturedRuntimeAndCleanupFailureKeepsItsPublisher` |
| AssertionError 等 Error 中断扇出、最终汇总或补偿，后续资源被遗漏 | 每项独立尝试并聚合失败；SDK 日志错误不覆盖原业务失败 | `LifecycleClosureTest`、`SdkFailureReportingTest` |
| Kafka handler 回退失败后可能继续提交；提交确认丢失后可能重复副作用 | runner 标记不可继续，保留主异常和提交结果未知证据，要求关闭并核对后再恢复 | `KafkaAdaptersTest.failedRewindStopsProcessingBeforeAnyLaterOffsetCanCommit`、`committedOffsetWithLostAcknowledgementCannotRepeatTheSuccessfulHandler` |
| 消费者初始化过晚漏掉被测消息；构造/缓冲失败遗漏资源或丢失尾部记录 | 动作前 ready；订阅失败释放已接管 consumer；消费 runner 超容量后停用 | `KafkaAdaptersTest` 的初始化、订阅和溢出场景 |
| Kafka 发送等待超时被当作明确失败，可能误重发 | 返回 OUTCOME_UNKNOWN，保留原发送，不隐式取消/重发 | `KafkaAdaptersTest.timeoutIsUnknownAndDoesNotCancelOrResendTheOriginalDelivery` |
| XXL-JOB 触发拒绝与已运行但暂不可关联混淆 | 区分 REJECTED/OUTCOME_UNKNOWN；当前 adapter 阻止不确定 job 的再次触发 | `XxlJobServiceTest.unknownSubmissionCannotBeMistakenForARejectionOrResubmitted` |
| JDBC 事务初始化失败遗留连接；本地 scheduler 关闭后任务长期显示运行/排队 | 关闭初始化失败的连接；关闭 scheduler 后区分 CANCELLED 与 UNKNOWN | `JdbcSessionTest`、`LocalJobServiceTest` |
| 证据仅验证哈希格式或类型，允许源码改变、缺模块文件仍通过 | 重算源码/制品哈希，要求各模块和规范文件完整，明确输入与运行范围 | `ci/test-evidence-gate.ps1`；统一入口拒绝继承筛选参数 |

运行时定向回归已完成 18 次、全部通过；这一数量仅指本轮定向集合，不是全仓最新总数。最终结果只接受统一入口结束后生成的当次证据，失败或仍为 RUNNING 的证据不能用于验收结论。

## 6. 企业推广前的阻塞项与补充顺序

P0 表示在允许多人对真实 dev/qa 执行有副作用测试前必须完成的环境验收；可以继续进行本地开发和受控演练。

| 优先级 | 剩余事项 | 责任与完成证据 |
|---|---|---|
| P0 | 公司 IAM/密钥注入、专用测试账号、环境/服务/资源准入，配置方法及自建客户端的权限约束 | 安全/环境负责人和业务 owner：账号权限矩阵、凭据轮换、拒绝越权/跨环境场景；Kafka topic/group、Dubbo 服务、jobId、数据库资源均有明确审批。 |
| P0 | Kafka、XXL-JOB、Dubbo 注册中心及公司 SDK 真实版本联调，未知结果的核对与业务幂等 | 对应平台和业务负责人：正常、鉴权、超时、网络失败、重复、任务关联和消费副作用记录；真实环境 `kafka-live`/`xxljob-live` 需显式启用，缺配置应失败，不以 skipped 充数。 |
| P0 | 共享资源多进程/多节点租约与 fencing；进程宕机后的补偿 | 基础平台与业务防护端：实际数据库的竞争、断连、过期、旧 owner 写入拒绝；有保留期限和审计记录的遗留数据处理方案。 |
| P0 | 100 人多业务域的配额、峰值并发与稳定性 | CI/环境/业务负责人：按业务域限制账号、SDK worker、topic、job 和数据库额度；真实峰值、跨节点及至少 8 小时稳定运行，记录延迟、失败、资源占用和清理残留。 |
| P0 | 私有仓库、发布权限、漏洞/许可证门禁、签名与归档权限 | 发布/安全负责人：公司平台上可追溯的 release、拒绝未授权发布与高风险依赖的证据，业务仓库能够从私有仓库重建。 |
| P1 | 持久化补偿台账、按 runId 的资源清理、失败分派与 flaky 治理 | 将异常/未知结果分配给 owner；受控清理只操作本次批准 namespace。重跑记录与原始失败并存，不能用自动重试覆盖第一次失败。 |
| P1 | 分布式追踪、契约与隔离环境验证 | 在现有 runId/caseId 上接入公司遥测，增加跨 HTTP/RPC/消息链路证据；引入真实平台临时环境与契约兼容回归前，先确定 CI 资源、版本和权限。 |
| P2 | 辅助生成用例、失败归因与风险选测 | 以人工审核的业务断言和精确测试全集作为基线；生成内容不能绕过准入、缩减必须执行集合或自动将未知结果判为通过。 |

SDK 强停、外部平台执行撤销、公司权限平台、分布式调度和业务补偿并非仅增加一个框架类就能完成。应先确认上述 P0 责任人与证据，再决定扩展接口和平台接入方式；对于“结果未知”，保留事实并停止不安全的重复操作是闭环的一部分。
