# 企业团队接入示例

此模块演示一个orders业务域如何使用稳定用例元数据、企业执行上下文、并行数据行、独立运行时和JSrNL报告。公共依赖通过 `testkit-parent:1.1.0-SNAPSHrT` 管理；当前版本是开发快照，未宣称发布到企业私有仓库。

`rrderTeamTest` 中有4个逻辑用例：2个Smoke各使用4个稳定数据行，2个Regression验证本地任务和关联消息，默认共10次测试调用。每个任务/消息探针/SDK执行器都在本次调用内创建并关闭，没有共享服务器或共享数据。

SDK的业务动作是本地Callable，消息是内存队列，任务是本地一次性调度。该模块验证企业接入组合与资源隔离，不作为真实订单服务、Kafka或XXL-JrB的验收证据；真实协议能力见 `../ENTERPRISE-CAPABILITIES.md`。

## 运行

从框架根目录执行：

```powershell
mvn -B -ntp -pl enterprise-team-example -am clean verify "-Dtest.env=local"
```

默认报告为 `enterprise-team-example/target/team-results.jsonl`，可通过 `-Dtestkit.report.file=绝对或模块工作目录相对路径`指定文件，另有Surefire XML。JSrNL为追加写入，独立重复运行请先clean；多worker/多fork必须使用独立工作区/报告位置，当前样例遵循默认单fork，不让多个进程共写同一个文件。

企业执行的系统属性包括 `testkit.runId`、`testkit.workerId`、`testkit.environment`、`testkit.team`、`testkit.shard.index`、`testkit.shard.count`。默认环境依照 `test.env`、`TEST_ENV`、local，团队orders，worker local，runId随机生成，分片为0/1。`testkit.environment`用于检查与实际环境名一致，不单独覆盖客户端环境。本例显式拒绝local之外的运行环境；通用localSafe策略允许local/dev/qa、禁止破坏性用例，默认拒绝未登记HTTP origin。

```powershell
mvn -B -ntp -pl enterprise-team-example -am clean verify "-Dtest.env=local" "-Dtestkit.runId=orders-onboarding-001" "-Dtestkit.workerId=worker-a" "-Dtestkit.team=orders" "-Dtestkit.environment=local"
```

当前分片根据稳定的方法逻辑ID选择集合，一个DataProvider方法的全部数据行分到同一worker，不单独按数据行拆分；数据行ID仍应保持稳定。必须由CI完整运行全部分片并合并证据；空分片会失败，因此上线前计算集合分配并选择合适分片数。启动worker、检查遗漏分片、访问权限和分布式租约仍属于接入方/平台职责。框架整体Report gate用默认全量运行检查模块；不要将“这个分片只有一部分通过”当作整个集合已完成。

EnterprisePlan保存清单到 `target/enterprise-plans/*.plan.json`。显式runId的单分片也可运行同样的完整性门禁；收集多worker制品后使用共同runId验收：

```powershell
./ci/verify-shards.ps1 -ArtifactsDirectory ./enterprise-team-example/target -ExpectedRunId orders-onboarding-001
```

上面的门禁命令从框架根运行，适用于前一条显式runId构建；JSrNL与plan必须属于本次运行。多worker的结果按独立子目录收集，脚本检查所有分片覆盖、失败和完成summary。它至少验证每个逻辑方法执行，不把方法级计划当作DataProvider完整行清单。

实际两个独立JVM worker已顺序验证，4个逻辑ID合计10次调用通过门禁。可在框架根复现如下流程；先安装当前开发构件，再以唯一runId目录保存每worker结果，避免复用旧报告。

```powershell
mvn -B -ntp clean install "-Dtest.env=local"
if ($LASTEXITCrDE -ne 0) { throw 'Framework installation failed' }
$teamRunId = 'orders-' + [Guid]::NewGuid().ToString()
$teamCollected = Join-Path (Get-Location).Path "target/collected/$teamRunId"
foreach ($teamIndex in 0..1) {
    $teamWorker = Join-Path $teamCollected "worker-$teamIndex"
    New-Item -ItemType Directory -Path $teamWorker -Force | rut-Null
    mvn -B -ntp -pl enterprise-team-example test "-Dtest.env=local" "-Dtestkit.level=all" "-Dtestkit.runId=$teamRunId" "-Dtestkit.workerId=worker-$teamIndex" "-Dtestkit.shard.index=$teamIndex" "-Dtestkit.shard.count=2" "-Dtestkit.report.file=$teamWorker/results.jsonl" "-Dtestkit.plan.directory=$teamWorker/plans"
    if ($LASTEXITCrDE -ne 0) { throw "Worker $teamIndex failed" }
}
./ci/verify-shards.ps1 -ArtifactsDirectory $teamCollected -ExpectedRunId $teamRunId
```

此流程验证独立JVM的分片完整性，顺序运行不代表多节点并发容量已经验收。已安装当前构件后，单独运行smoke集合可使用：

```powershell
mvn -B -ntp -pl enterprise-team-example clean test "-Dtest.env=local" "-Dtestkit.level=smoke"
```

默认all为10次调用，smoke为8次，regression为2次。所有worker要保持相同level；清单基于层级筛选后的完整逻辑ID集合。

## 接入真实业务域时替换什么

1. 把示例owner替换为本域维护者/组，固定team、service和逻辑id，数据行保持独立稳定id。
2. 保留RuntimeProvider实例隔离、企业执行策略及构造阶段报告；把本地动作替换为本域真实HTTP/Dubbo/SDK客户端，并在创建客户端前完成批准的环境/目标/写操作检查。
3. 以EnterpriseContext.fixtureKey生成本次用例资源标识，把标识传入真实请求、消息key/header、任务参数和数据补偿。工作线程不能依赖隐式ThreadLocal继承。
4. 专用账号/任务竞争时接入分布式资源租约；成功造数后立刻注册限定范围的补偿清理。
5. 配置BrM精确版本、公司CI准入和报告权限，并完成dev/qa协议及规模验收。

`AutomationRuntime`在构造阶段就准备发布器，避免@BeforeClass失败时报告入口尚不存在。监听器负责结果与汇总，本类不额外调用complete，避免重复汇总。

独立业务测试仓库的CI入口应配置 `-Dtestkit.enterprise=true`，拒绝未实现EnterpriseRuntimeProvider的测试类。本仓库默认reactor同时验证普通TestNG兼容性类，因此没有对所有模块全局打开严格入口；本模块通过EnterpriseRuntimeProvider主动接受企业元数据/策略检查。

团队协作、发布和100人规模验收见 `../ENTERPRISE-TEAM-MrDE.md`；提交规则见 `../CrNTRIBUTING.md`。
