# 100人团队的企业自动化模式

更新日期：2026-10-15。当前统一开发版本为 `1.1.0-SNAPSHOT`。技术能力总览见 `ENTERPRISE-CAPABILITIES.md`。本文解释多人协作、执行隔离、CI准入、版本分发及规模验收。

本轮完整节点审查见 [闭环审计](docs/LIFECYCLE-AUDIT.md)，命名与旧入口迁移见 [工程规范](docs/ENGINEERING-STANDARDS.md)。完整本地验收与Jenkins使用 `verify-lifecycle.ps1`；最新结果以当次证据为准，本文第8节保留上一轮历史规模证据。

**100人表示多人、多业务域可以共同维护和使用框架，不表示将线程数改成100就完成企业化。** 本仓库提供团队模式基础代码、接入样例和治理文件；公司身份权限、分布式资源协调、真实环境容量及供应链系统仍需接入和验收。没有实际100人/多节点验收证据时，不宣称已通过该规模认证。

## 1. 使用方式：平台核心由少数人维护，业务团队独立消费

| 层级 | 维护内容 | 消费方式 |
|---|---|---|
| testkit-parent | JDK、插件、编译与验证默认值 | 框架及认可的测试项目继承；已有公司父POM可保留 |
| testkit-bom | 框架/适配器及关键依赖版本组合 | 业务项目导入精确版本BOM，适合已有公司父POM的项目 |
| base-test-toolkit | 运行时、鉴权SPI、环境、隔离、报告和通用测试契约 | 各业务域按版本依赖，不复制公共源码 |
| enterprise-adapters | Dubbo/Kafka/XXL-JOB等平台协议 | 按需选择；兼容版本单独记录 |
| enterprise-team-example | 企业接入组合、门禁与隔离的本地示例 | 新团队先跑样例，再替换为本域账号、资源和业务断言 |
| 业务域测试仓库 | 业务用例、造数、断言、回归集合 | 各域独立发布、执行和维护，不全部堆到框架仓库 |

当前工作区是学习、框架和样例的组合。推荐正式使用时将公共框架发布到公司 Nexus/Artifactory 等制品系统，业务仓库只依赖批准版本。`mvn install` 只解决本机可用，不解决100台机器的版本分发。父POM治理构建，BOM治理依赖版本，两者不能替代访问权限或实际兼容性验证。

没有公司父POM的消费项目可继承统一构建规范，业务自己的版本不会改变框架依赖版本：

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.testkit</groupId><artifactId>testkit-parent</artifactId>
    <version>1.1.0-SNAPSHOT</version><relativePath/>
  </parent>
  <groupId>com.example.qa</groupId><artifactId>orders-tests</artifactId><version>9.9.9</version>
  <dependencies>
    <dependency><groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

已有公司父POM时保留它，仅导入BOM管理依赖；公司父POM仍负责测试插件、JDK和门禁。

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.testkit</groupId><artifactId>testkit-bom</artifactId><version>1.1.0-SNAPSHOT</version>
      <type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency><groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId><scope>test</scope></dependency>
</dependencies>
```

这两种业务版本9.9.9的消费模型已通过Maven effective-pom验证，框架依赖均保持1.1.0-SNAPSHOT。当前示例使用开发快照；正式交付替换为已发布的不可变版本，并配置公司仓库。尚未真实发布到私有仓库。

## 2. 一个可执行的100人分工方案

以下是起步建议，可按现有人力调整，而不是新增100个专职岗位。

| 角色 | 示例人数 | 对结果负责 |
|---|---:|---|
| 框架维护组 | 5 | 公共API、适配器兼容矩阵、回归、版本和迁移 |
| CI/环境/安全协作组 | 5 | runner、环境准入、账号、私有仓库、密钥与扫描 |
| 业务域质量负责人 | 10 | 每域测试设计、用例所有权、风险覆盖与失败分派 |
| 业务测试贡献者 | 80 | 用例、数据、业务断言、日常问题定位与补偿清理 |

建议划分10个业务域，每域约8名贡献者和1名质量负责人。每域有自己的测试仓库、namespace、资源配额、报告与值守归属，公共升级由维护组组织试点。公共核心、环境配置、流水线、发布分别指定主负责人和备份，避免单人知识与发布权限瓶颈。

## 3. 运行前先做准入，再创建会产生副作用的客户端

企业测试入口应确定团队、业务域、环境、runId、逻辑用例ID、分片、资源namespace及允许的能力，并验证 endpoint、任务/消息/数据库资源是否批准。执行上下文只放标识与策略引用，不放密钥。

- 默认仅允许 local/dev/qa等批准环境；生产探测必须由独立受控入口和公司权限策略明确授权。
- 配置值必须与环境资源匹配，不能仅看 `TEST_ENV=qa` 就默认URL、topic、jobId也是qa。公司网关/DNS/路由变化也要纳入审核。
- Kafka只能使用专用测试topic/group；XXL-JOB使用批准的专用任务，关闭其自动cron并禁止并发人工触发；数据库账号按域和环境使用最小权限。
- HTTP、消息发送、任务触发等写操作必须注册清理或明确副作用处理。未知结果不能通过再次创建/触发来“修复”。

当前框架ExecutionPolicy检查环境名、团队、服务名集合及破坏性用例开关。TargetPolicy额外提供环境/服务对应的HTTP origin白名单，ApiClient在企业上下文中请求前校验scheme/host/port；默认localSafe允许local/dev/qa、拒绝破坏性用例、拒绝未登记的HTTP目标。它不按URL路径配置权限，也不能从请求内容判断用例是否正确声明了破坏性。Kafka/topic、Dubbo、jobId和数据库资源白名单仍需接入方在调用前执行。框架策略不等于SSO/RBAC，不会阻止绕过ApiClient的自建客户端，也不能证明企业网络ACL已正确配置。

例如，以下execution可由业务测试的EnterpriseRuntimeProvider返回。`qa-order.example.internal`仅为替换用的示例origin，服务名必须与@TestCaseMetadata.service一致；密钥仍从公司凭据服务注入。

```java
var targets = new TargetPolicy(Map.of("qa/order-api",
        Set.of("https://qa-order.example.internal")));
var policy = new ExecutionPolicy(Set.of("qa"), Set.of("order-api"), false, targets);
var execution = new EnterpriseExecution(
        new RunMetadata("orders-qa-run-001", "worker-0", "qa", "orders"),
        policy, new Shard(0, 1));
```

普通兼容模式没有企业上下文时不自动应用该白名单。企业入口必须采用EnterpriseRuntimeProvider，并让业务调用经受策略保护的客户端；账号授权、runner访问范围和策略文件变更权限由公司系统共同执行。

企业业务测试入口应启用 `-Dtestkit.enterprise=true`，拒绝未采用EnterpriseRuntimeProvider的普通测试类。框架自身兼容性回归包含普通TestNG类，默认整个reactor不全局打开该标志；企业接入示例依靠EnterpriseRuntimeProvider主动执行元数据与策略准入。公司业务流水线应明确选择严格入口，不能假设任意依赖了框架的测试已自动接受企业规则。

## 4. 多人、多进程运行的隔离契约

每次运行生成runId，结合团队、业务域、环境形成namespace；方法逻辑ID用于历史追踪及分片，数据行也应有稳定ID供业务报告关联。namespace需适配数据库/topic/账号等资源命名限制，保存原始运行标识到报告。随机UUID可用于本次执行关联，但不能用于稳定分片。

| 资源 | 隔离办法 | 必须验证的故障路径 |
|---|---|---|
| 运行时/身份 | RuntimeProvider、线程作用域、显式worker传播 | 清理异常仍释放、工作线程复用不串上下文 |
| 测试数据 | namespace/runId标记、唯一业务键、限定补偿 | 远程已提交、超时未知、进程退出后的遗留数据 |
| 报告 | 每job/run/shard独立文件与工作区 | 失败归档、缺报告拒绝、重复汇总和敏感值 |
| 消息 | 专用topic、独立group、发送前ready、业务关联字段 | 消费分区再均衡、重复、延迟、清理与配额 |
| 任务 | 专用jobId、串行触发或分布式租约 | 多触发者、日志关联歧义、超时后仍运行 |
| 独占账号/设备 | 有期限且带owner/token的租约 | 竞争、超时、过期续租失败、旧持有者误释放 |

线程隔离及本地租约只覆盖单JVM。框架另已提供JdbcResourceLeaseProvider：通过数据库时间和原子条件更新实现获取、TTL、有效性检查、续租、过期接管和所有权释放，返回递增fencingToken。管理员先执行 `config/resource-leases.sql`，运行账号只需对应表SELECT/INSERT/UPDATE，框架不自动建表。

```java
var leases = new JdbcResourceLeaseProvider(dataSource, "testkit_resource_lease");
var acquired = leases.tryAcquire("qa:orders:xxljob:42", Duration.ofSeconds(30));
if (acquired.isEmpty()) throw new IllegalStateException("Test resource is busy");
try (var lease = acquired.orElseThrow()) {
    if (!lease.valid()) throw new IllegalStateException("Test resource lease expired");
    long fence = lease.fencingToken(); // 传给有fencing校验能力的业务防护端
    // 在租约有效期内执行受保护操作；长操作需要提前续租
    if (!lease.renew(Duration.ofSeconds(30)))
        throw new IllegalStateException("Test resource lease renewal failed");
}
```

TTL范围为1秒至1小时。H2已验证16个竞争调用仅一个成功、续期和旧持有者不能释放新租约；公司生产数据库的SQL/时间精度、网络故障和实际多节点验收尚未执行。业务防护端必须拒绝过期fence，否则旧进程仍可能写入。不能用静态Map或Jenkins单Job串行替代跨节点租约；租约到期不会撤销已发出的远程请求。

当前分片按方法的稳定逻辑ID分配，一个DataProvider方法的全部行同属一分片；没有自动按数据行或耗时拆分。空分片直接失败，需先计算集合分配并选择合适分片数。EnterprisePlan保存完整/选中逻辑ID、planHash和分片元数据到 `target/enterprise-plans/*.plan.json`，可用testkit.plan.directory指定目录。分片不自动启动worker或提供跨节点调度。

`ci/verify-shards.ps1`执行全分片门禁：重算完整ID集合SHA256，核对所有index、唯一worker、同run/环境/团队/分片数、选中集合无交叉且完整覆盖；逐个核对JSONL的worker和逻辑ID，拒绝失败/跳过/配置失败、缺用例、缺报告、缺完成summary、计数不一致和重复执行记录。用例的多数据行允许多个不同caseId的PASSED事件；当前验收至少检查每个方法逻辑ID有一条事件，不证明DataProvider所有预期行都执行。

合并目前只接受一个业务域、一个完整planHash/计划，不混合多个TestNG context或域的计划。每worker独立JSONL；将所有worker制品保留目录结构收集后运行：

```powershell
./ci/verify-shards.ps1 -ArtifactsDirectory ./target/collected -ExpectedRunId orders-qa-run-001
```

planHash证明逻辑ID集合相同，不证明代码或数据版本相同；CI仍需从同一Git SHA、BOM和数据版本启动全部worker。有人修改过滤集合或分片数会改变分配，门禁会拒绝计划漂移。脚本回归见 `ci/test-shard-gate.ps1`，它验证32种接受/拒绝场景。

## 5. 分层执行与合并门禁

| 入口 | 目的 | 环境与副作用 | 准入证据 |
|---|---|---|---|
| 开发本地 | 快速验证公共代码与业务逻辑 | 默认本地替身/嵌入服务 | 本地测试通过 |
| 每次PR | 框架/适配器契约及企业样例回归 | local，无企业凭据 | clean verify + 报告门禁 + review |
| dev smoke | 新适配及业务链路连通 | 专用dev资源 | 准入策略、清理和版本证据 |
| qa回归 | 跨层业务与异步副作用 | 专用qa数据、配额与租约 | 全分片完成、业务断言、清理结果 |
| 发布候选 | 固定版本可被下游消费 | 试点业务库及公司扫描 | 兼容性、迁移、制品与审批 |

`verify-framework.ps1`负责读取预期模块Surefire报告，拒绝失败、错误、报告缺失和零执行。`-ReportOnly`供已经完成构建的CI使用。Jenkins模板还运行分片门禁回归和企业示例完整计划验收，并归档plan清单。接入方式见 `ci/README.md`；仍需实际Jenkins验证并配置分支保护。

业务入口可通过 `testkit.level=all/smoke/regression`选择集合，默认all。先验证元数据及策略，再筛选层级和分片；层级没有匹配用例时失败，清单记录层级筛选后的完整集合。全部worker必须使用同一层级选择。

禁止以全跳过、失败后自动无限重跑或只看测试进程退出0掩盖缺失报告。需要隔离不稳定用例时，登记负责人、缺陷、影响范围及到期时间，保留首次失败证据；隔离不应增加发布覆盖率。

规模扩展先分片和增加worker，再依据服务容量调每worker并发。每域配置HTTP连接、SDK线程/队列、消息消费者、任务触发及数据库连接配额。100名开发者同时提交代码并不要求100名worker同时访问共享qa服务。

## 6. 版本、私有仓库与兼容性

正式发布采用精确版本和不可变release；未发布修改放`-SNAPSHOT`/预发布版，不反复覆盖`1.0.0`。业务库通过BOM固定测试过的组合，发布记录同时保存父POM/BOM/核心/适配器版本、Git SHA、测试报告和依赖清单。

发布顺序为：完整回归 → 公共API/协议兼容审查 → 公司依赖漏洞/许可证/密钥扫描 → 业务域试点 → 发布审批 → 私有仓库发布 → 下游最小样例验证。公司可按制度加入SBOM、签名和来源证明；当前仓库不会在没有地址/凭据/权限的情况下自动发布。

public API/默认行为/配置语义的破坏性变更提升major并附迁移示例；兼容扩展提升minor，修复提升patch。XXL-JOB的2.4.1/3.1.1旧式契约不能推断为任意3.x兼容；更换Kafka/Dubbo客户端后重新运行契约和真实环境验证。

## 7. 失败分派、可观测性与运维

运行记录至少包含团队/域/环境、稳定用例ID、runId、caseId、版本、Git SHA、分片、耗时、结果、准入/租约与清理结果。当前JSONL采用结构化白名单；平台接入时须检查哪些字段已经输出，不能因为拥有上下文对象就假设全部已上传。

业务域负责人先分辨被测服务缺陷、环境故障、数据/账号冲突及框架缺陷。框架维护组处理公共协议和生命周期，环境组处理资源与连通性。建议形成每周的失败分类、首次通过率、隔离到期、资源遗留与版本使用分布，不用单一“通过率”替代问题定位。

设置报告保留与访问权限，业务用例详细报告中的参数也可能敏感。定期验证线程/连接/端口/租约/测试数据是否释放；长任务忽略中断时单独记录并处理。框架提供关联与发布SPI，不等于已经接入APM、告警或公司的测试管理平台。

## 8. 必须通过的规模验收

以下是建议的验收方案和起始目标，不是已取得的测试结果。上线前按公司业务容量确认最终阈值并保存实际报告。

2026-10-13上一轮本地证据为：JDK 21.0.10执行、编译目标Java17；默认reactor93条顶层回归（核心56、适配器8、订单演示19、企业示例10）。核心56条中包含100线程同时运行的runtime/identity/trace/enterprise scope及fixture key隔离测试；SDK上下文传播由企业示例的并行数据行验证，不是100个SDK并发。实际两个独立JVM worker顺序执行后通过完整分片门禁：4个逻辑ID、10次调用；smoke单分片为2个逻辑ID、8次调用。报告门禁9场景（1接受/8拒绝）、分片门禁32场景均通过。Jenkins的JDK17节点尚未实际验证；这些本地证据不等于多节点同时执行或100人企业权限验收。

| 验收项 | 执行方式 | 建议通过条件 |
|---|---|---|
| 100人权限和所有权 | 注册各域账号/组，抽查仓库、CI、环境权限 | 贡献者/维护者/发布者职责分离；跨域资源无误授权 |
| 多域并行 | 10个域、每域2个CI任务，同时执行固定集合 | namespace无冲突、上下文不串域、报告归属正确 |
| 分片完整性 | 足够大的同一集合以1/2/4/8分片执行并比较稳定ID，先确认每分片非空 | 全量集合相同，分片间无重复且无遗漏；小集合空分片按预期失败 |
| 共享资源竞争 | 多进程/多节点抢占同一账号或jobId | 只一个有效owner；旧token不能释放新租约 |
| 进程/节点故障 | 强停持有租约和已造数的worker | 租约可恢复，遗留数据能按namespace清理并审计 |
| 环境误配 | qa上下文配dev HTTP origin/未批准环境；其他资源另接白名单 | HTTP已覆盖允许/拒绝和无副作用本地测试；真实目标和job/topic准入仍需验收 |
| 真实协议 | 各环境Dubbo注册中心/Kafka/XXL-JOB业务用例 | 认证、超时、失败、重复与业务副作用全部有证据 |
| 长时间稳定性 | 依据目标并发运行至少8小时，监测资源 | 无持续线程/连接/内存增长；清理和租约泄漏为0 |
| CI门禁攻击/误配 | 缺报告、全skip、配置失败、遗漏分片 | 均不能得到发布成功状态；原失败证据可定位 |
| 版本发布与回滚 | 新版试点、私有仓库下载、回切旧BOM | 产物可复现定位，旧版仍可消费，不覆盖release |

发布profile缺少HTTPS仓库URL时，Maven validate已验证拒绝。真实私有仓库发布、扫描、签名及下游远程下载仍未执行。

验收前先让2个业务域试用，修正资源与用例规范；随后扩到10个域，再组织实际峰值运行。真实环境并发规模、实际生产数据库及多节点租约验收、企业身份权限、扫描系统和私有仓库发布是团队上线的必要工作，不会因本地样例通过而自动完成。

## 9. 上线检查记录

每个业务域保留一份版本化接入记录：域/负责人、使用BOM版本、批准环境与策略版本、资源清单及配额、凭据来源、分片策略、报告位置/权限、清理方案、联调结果、遗留风险和验收人。

框架学习入口仍是 `base-test-toolkit/LEARNING-GUIDE.md`。先掌握运行时生命周期和一个HTTP业务用例，再阅读企业接入样例、任务及消息链路；团队模式增加协作与执行约束，不替代业务场景的设计。
