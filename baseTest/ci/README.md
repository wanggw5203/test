# CI 接入说明

`Jenkinsfile` 是提交到仓库的本地模板，默认只运行本地契约及回归测试，不触发 Kafka、XXL-JOB 或其他 dev/qa 服务。它没有发布制品、发送通知或读取公司凭据的步骤，尚未在实际 Jenkins 服务中执行或通过其在线语法校验。

## 节点与仓库接入

1. 为 Jenkins 配置可执行 Java 17、Maven 3.9 和 PowerShell 7.2以上的节点，添加 `testkit-jdk17` 标签；在工具配置中使用 `jdk17`、`maven39`，或修改模板中的名称。
2. 配置 Pipeline/Multibranch Pipeline，脚本路径为 `baseTest/ci/Jenkinsfile`。当前 Git 根目录是框架的上一级，`FRAMEWORK_DIR` 默认 `baseTest`；抽成独立仓库后脚本路径改为 `ci/Jenkinsfile`，选择 `.`。
3. 安装并启用 Declarative Pipeline、相应 SCM、Pipeline Durable Task（提供 `pwsh`）及 JUnit 插件。节点必须实际安装 `pwsh`，JDK/Maven 工具配置不会安装 PowerShell。
4. 配置公司 Maven 镜像与信任证书。模板使用工作区下 `.m2/repository`，不依赖构建机用户目录里的开发者缓存；有仓库凭据时使用 Jenkins 管理的文件/凭据注入，不提交带密码的 settings.xml。
5. 将整个 Pipeline 的成功状态设为合并准入；不能只看 JUnit 趋势页。报告中出现跳过不能代表测试执行成功。

模板调用根目录 `verify-lifecycle.ps1`：生成独立runId，执行全量clean verify、Spotless和CycloneDX SBOM，再执行模块报告/企业计划门禁、四类门禁回归，最后形成并复验本次源码及制品证据。Maven只构建一次，后续门禁读取本次结果。入口固定local/all/0号单分片，拒绝继承的测试过滤、跳过和部分reactor参数；定向开发测试使用普通Maven命令。门禁检查预期4个测试模块、失败/错误、跳过、报告缺失/陈旧及计数真实性。

```powershell
./verify-lifecycle.ps1
./verify-lifecycle.ps1 -RunId framework-pr-001 -MavenRepository ./.m2/repository
```

生成的证据记录Git提交及工作区是否有改动，另外计算实际构建输入指纹；工作区不必已提交，但构建过程中不能修改源码、配置或脚本。

模板使用30分钟整体超时，保留30次构建记录和14次制品。它们是初始值，不是生产 SLA。超时终止 JVM 不会撤销已发生的远程写入，因此真实环境测试必须另有资源清理及遗留数据巡检。

`disableConcurrentBuilds()` 只串行化同一个 Jenkins Job。多分支、多团队的 Job 仍可能同时运行，必须使用独立工作区、报告文件和测试 namespace；争抢同一账号/任务时需要分布式租约。该选项不能代替跨团队锁。

## 报告与安全边界

- `post always` 保存根 `target/verification-summary.json`、`verification-evidence.json`、`testkit-sbom.json/xml`、Surefire/Failsafe、模块 JAR、约定位置的 JSONL和enterprise-plans清单，构建失败也保留诊断。编译失败可能没有报告，归档步骤允许空结果，但原构建仍失败；成功构建的报告缺失由报告门禁拒绝。
- JSONL 应放到各模块 `target/*.jsonl` 或 `target/reports/**/*.jsonl`。多进程不能写同一个 JSONL；每个分片单独保存后再聚合。
- Surefire/XML/HTML 可能包含业务参数或原始错误。公司应限制报告下载权限与保留时间；凭据不放进数据行、用例名、命令行或异常文本。
- CycloneDX显式包括test scope，避免漏掉测试框架本身依赖；它是清单而非漏洞扫描。源码与制品SHA256用于一致性复验，不等同于加密签名、受信来源或供应链认证。
- 不为外部贡献分支注入企业账号或开放内网 runner。依赖漏洞、许可证、密钥扫描以及不可变制品发布需接入公司已有扫描/制品系统，当前模板没有假装已完成这些检查。

## dev/qa 联调应单独建任务

联调流水线应由有权限的人员触发，并绑定 Jenkins 环境权限、专用凭据、批准的 endpoint/topic/jobId 和并发配额。公司 CI 负责账号授权，框架内的环境策略负责调用前检查；本地配置不是身份认证系统。

联调至少记录 Git SHA、框架/BOM 版本、环境、团队、业务域、runId、分片数量、准入策略版本、报告地址与清理结果。Kafka/XXL-JOB 现有 live profile 会实际发送消息/触发任务，不能直接加到所有 PR 的默认流水线。

发布应使用另一个仅针对受保护 tag 的 Job：完整门禁 → 公司扫描 → 兼容性检查 → 审批 → 私有仓库发布 → 下游样例验证。不在此模板附带未知仓库地址或占位密码。

## 全分片验收

每个worker使用同一runId、Git SHA、BOM和完整用例选择集合，设置自己的workerId及shard.index/count，使用独立工作区。EnterprisePlan默认保存 `target/enterprise-plans/*.plan.json`；JSONL每worker独立文件，保存完成summary。聚合Job收集全部worker制品到不同子目录后运行：

```powershell
./ci/verify-shards.ps1 -ArtifactsDirectory ./target/collected -ExpectedRunId orders-qa-run-001
./ci/test-report-gate.ps1
./ci/test-shard-gate.ps1
```

门禁只接受单业务域的单一完整计划。它重新计算Ordinal排序逻辑ID、LF连接的SHA256，拒绝缺分片、重复worker/index、漂移计划、选中集合重叠/遗漏、错环境、缺报告/用例/summary、计数不符，以及同run的任何FAILED/SKIPPED事件。重复执行caseId也阻断，DataProvider不同caseId的多条成功记录允许；至少一条每逻辑ID的证据不能证明所有数据行都执行。

32个脚本场景已经在本地PowerShell 7验证，包含正向、缺分片/报告/逻辑用例事件、环境错配、失败/跳过/配置失败、计划漂移、hash伪造、重复与summary截断。不混合其他run的plan；其他run的JSONL事件不参与本次结果。清单hash不包含Git/BOM/测试数据内容，调用方必须另外冻结这些版本。

2026-10-13历史默认构建为93次顶层回归；最新数量以本次 `target/verification-summary.json` 为准。本轮报告门禁9个、分片门禁32个、证据门禁24个、参数准入16个场景，共81个。历史两个JVM worker的4逻辑ID/10调用和smoke单片2逻辑ID/8调用已通过完整性验收。测试运行时是JDK21.0.10，编译目标Java17；模板选择的实际JDK17 Jenkins节点和服务端Pipeline仍未验证。顺序启动两个本地JVM不证明多节点同时执行的容量或网络故障恢复。

## 证据复验

保留同一次构建的完整目录结构及相同源码字节，使用证据中的runId执行：

```powershell
./ci/verify-evidence.ps1 -ExpectedRunId framework-pr-001
./ci/test-evidence-gate.ps1
```

复验拒绝失败/未完成状态、缺检查、源码变化、模块报告/二进制/SBOM/计划缺失、重复路径、内容摘要或长度变化、目录越界及链接文件。归档者同时控制证据和所有文件时可重新生成摘要，因此公司需要可信CI、访问控制和后续签名，不能把本地索引当作防伪认证。

## 已核对的官方资料

- [Jenkins Pipeline 语法](https://www.jenkins.io/doc/book/pipeline/syntax/)：工具名需预先配置，并说明并发、超时及保留选项。
- [JUnit Pipeline 步骤](https://www.jenkins.io/doc/pipeline/steps/junit/)：JUnit XML（含 TestNG 生成的同类报告）导入。
- [保存测试和制品](https://www.jenkins.io/doc/pipeline/tour/tests-and-artifacts/)：在 `post always` 中保留报告。
- [archiveArtifacts](https://www.jenkins.io/doc/pipeline/steps/core/)：归档只读取工作区内文件。
