# 企业团队模式本地验收记录

代码版本：`1.1.0-SNAPSHOT`。本记录针对本工作区源码，不代表已经部署到公司平台或完成实际100人规模认证。

## 2026-10-15闭环与规范验收

本轮执行根目录 `verify-lifecycle.ps1`：核心71、协议适配器23、订单演练19、团队示例10，合计 **123次顶层测试全部通过**，失败、错误、跳过均为0。另有报告门禁9、分片门禁32、证据门禁24、完整执行参数准入16个场景，共81个门禁场景；实际结果以本次统一入口输出为准。

已检查规范新注解及旧注解兼容、分片清单完整归属、配置阶段运行时、清理/报告错误传播、Kafka未知结果与位点、XXL-JOB触发结果分类、JDBC初始化释放和任务关闭终态。Spotless格式检查、包含测试依赖的CycloneDX1.6 JSON/XML SBOM以及源码/制品证据复验随统一入口执行。

工具为Maven3.9.16、JDK21.0.10，Java编译目标17；固定依赖组合由父POM管理。本轮安全版本审查及剩余扫描范围见 [依赖审查](docs/DEPENDENCY-REVIEW.md)。最新runId、源码指纹与实际制品SHA256见 `target/verification-evidence.json`，实际数量见 `target/verification-summary.json`。生成目录可被clean覆盖，CI应归档。完整节点结论见 [闭环审计](docs/LIFECYCLE-AUDIT.md)。

最终统一入口已通过：runId为 `verification-c08baa2187ab4094872f0eaf17201349`，源码指纹为 `48e6ee0b80bd9c724c768b69bfef93ce678fb6cb5c6a2c6f321fa869412002ce`，12个必需制品记录均通过复验。维护依赖调整后再次执行全量回归，123次测试与81个门禁场景均通过。已安装本次源码对应的父POM/BOM/核心及适配器开发构件；安装步骤没有作为测试成功证据。

## 2026-10-13历史验收

以下保留上一轮执行条件及93次回归记录，用于追溯；不作为本轮新增修复的替代证据。

## 1. 运行条件与完整回归

- 操作系统：Windows；脚本使用 PowerShell 7。
- Maven：3.9.16；实际运行 JDK：21.0.10；Java 编译目标：17。
- 执行：根目录 `mvn -B -ntp clean verify`，统一传入本次 runId，随后执行报告和计划完整性门禁。
- 最终运行标识：`ci-final-0d8dc944-e460-421f-91aa-a81b75cdcb82`。

| 模块 | 顶层测试调用 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| base-test-toolkit | 56 | 0 | 0 | 0 |
| enterprise-adapters | 8 | 0 | 0 | 0 |
| java-api-lab | 19 | 0 | 0 | 0 |
| enterprise-team-example | 10 | 0 | 0 | 0 |
| 合计 | 93 | 0 | 0 | 0 |

构建后的数量证据为 `target/verification-summary.json`，详细结果为各模块 `target/surefire-reports/TEST-*.xml`。target 是生成目录，下一次 clean 会覆盖，CI 应归档这些文件。部分回归会在内层故意制造配置失败，再由外层断言检验；表中统计外层测试，不把受控故障当作本次框架构建失败。

完整验证之后，已执行 `mvn -B -ntp install -DskipTests` 将本次父 POM、BOM、核心与适配器等构件安装到本机 Maven 仓库。此步骤复用刚通过的构建结果，不将跳过测试的安装步骤当作额外测试证据；旧版1.0.0构件未覆盖。

## 2. 企业能力的实际证据

| 验收项 | 已执行内容 | 证据边界 |
|---|---|---|
| 百并发上下文隔离 | 100 个线程同时绑定各自 runtime、identity、trace、enterprise scope；验证上下文归属、清理及100个不同 fixture key | 属于核心56条中的一个回归；单 JVM，不是100名员工或100个远程 worker |
| SDK 上下文传播 | 企业样例并行数据行通过 SdkInvoker 验证 fixture key 与 trace 保持一致 | 使用本地 Callable；不是百并发真实 SDK 容量验收 |
| 资源租约竞争 | 16 个独立 provider 调用竞争一个资源，仅一个成功；释放后 fence 递增；过期旧 owner 无法续期或释放新租约 | 单 JVM、H2 数据库；生产数据库与多节点故障未测试 |
| HTTP 目标准入 | 环境/服务对应 origin 的允许和拒绝、缺白名单拒绝及请求前拦截 | 自建客户端和其他协议资源仍需公司入口与权限约束 |
| 稳定分片 | 稳定 ID 的唯一分配、空分片拒绝、缺元数据/重复 ID/依赖拒绝、计划落盘及摘要校验 | 方法级分片；DataProvider 全部行跟随方法 |
| 两个独立 worker | 两个独立 Maven/JVM 顺序执行，共同 runId、分片0/1，各自报告/计划；合并覆盖4个逻辑 ID、10次调用 | 顺序执行，不代表跨节点同时运行 |
| Smoke 入口 | 严格企业入口运行 smoke 集合，覆盖2个逻辑 ID、8次调用，通过完整性门禁 | 层级筛选不绕过元数据和策略检查 |
| 最终默认样例 | 最终 clean verify 后，使用统一 runId 验收单分片全部计划 | 4个逻辑 ID、10次调用，完整 summary |
| 报告门禁 | 1个接受、8个拒绝场景通过：失败、全skip、部分skip、伪造计数、无测试、DTD、过期报告、缺报告 | `ci/test-report-gate.ps1`，合计9个场景 |
| 分片门禁 | 2个接受、30个拒绝场景通过，包括漏分片、计划漂移、重复执行、缺用例事件、失败/跳过/配置失败、缺summary | `ci/test-shard-gate.ps1`，合计32个场景 |
| 外部项目消费 | 业务版本9.9.9的项目分别继承父 POM、导入 BOM，Maven effective-pom 均解析 toolkit 为1.1.0-SNAPSHOT | 本地构件消费；不是从公司私有仓库下载 |
| 发布入口 | 缺少 HTTPS release/snapshot 仓库 URL 时，enterprise-publish 的 Maven validate 拒绝 | 未执行远程 deploy、签名或公司扫描 |
| Jenkins 模板 | 内嵌 PowerShell 脚本解析；包含构建、报告门禁、门禁回归、样例计划验收与制品归档 | 未在真实 Jenkins 服务端运行；JDK17 runner 仍待验证 |

HTTP订单、Dubbo直连等协议回归与 XXL-JOB/Kafka 的模拟契约边界，见 [能力指南](ENTERPRISE-CAPABILITIES.md) 的验证矩阵。

## 3. 本地复现入口

从框架根目录执行：

```powershell
./verify-framework.ps1
if ($LASTEXITCODE -ne 0) { throw 'Framework verification failed' }
./ci/test-report-gate.ps1
./ci/test-shard-gate.ps1
```

报告和计划的关联验证需要给 Maven 与门禁相同 runId；两个 worker 的完整复现命令见 [团队示例](enterprise-team-example/README.md)。不能只运行其中一个分片后宣布整个集合通过。

## 4. 公司上线前尚需取得的证据

真实 dev/qa 的 Kafka、XXL-JOB 管理端/执行器、Dubbo 注册中心及公司 SDK 需联调；确认认证、失败、超时、关联、业务消费副作用与清理。租约需在实际生产同型数据库上完成多进程/多节点竞争和故障恢复验证，业务防护端需实施 fence 校验。

企业 SSO/RBAC、密钥系统、网络与跨域资源权限、私有仓库、扫描/签名、CI 分支保护均依赖公司平台配置。至少8小时稳定性、真实峰值并发、100人权限与多业务域协作验收尚未执行。具体验收方案和接入职责见 [企业团队模式](ENTERPRISE-TEAM-MODE.md)。
