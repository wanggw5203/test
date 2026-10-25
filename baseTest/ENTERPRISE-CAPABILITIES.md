# 企业自动化框架能力、审计结论与使用指南

更新日期：2026-10-15。本文对应当前源码及实际验证结果，是后续查看框架能力的主入口。

100人团队的协作、严格入口、稳定分片、资源租约、CI与发布流程见 [企业团队模式](ENTERPRISE-TEAM-MODE.md)；可运行的新团队样例见 [enterprise-team-example](enterprise-team-example/README.md)。

本轮实际执行的测试数量、运行条件及未验证范围见 [本地验收记录](ENTERPRISE-ACCEPTANCE.md)；逐步阅读源码与动手路线见 [学习指南](base-test-toolkit/LEARNING-GUIDE.md)。

2026-10-15新增 [闭环节点审计](docs/LIFECYCLE-AUDIT.md)、[工程命名与迁移规范](docs/ENGINEERING-STANDARDS.md) 和 [技术补齐路线图](docs/TECHNOLOGY-ROADMAP.md)。完整验收入口为 `verify-lifecycle.ps1`，包括统一格式、SBOM和源码/制品证据复验；模块报告检查仍使用原脚本。

## 1. 审计结论

原项目是一套轻量 TestNG 工具包。上一轮已有 HTTP、数据驱动、身份、断言及结果接口，但 Dubbo、SDK、消息中间件、真实任务平台、多环境配置和跨测试实例隔离尚未完整落地。

本轮增加了可执行的分层适配、资源管理和回归验证，已具备企业接口自动化框架的基础结构。**企业环境认证仍需真实 dev/qa 中间件、业务用例、并发压力和安全配置的联调证据；当前不能仅凭本地测试宣布所有企业场景达标。**

团队模式在此前73条回归的基础上增加了企业策略、分片、100并发上下文、JDBC租约竞争、计划清单和团队样例。最新完整测试数量见根目录 target/verification-summary.json；不将故意失败后被外层断言验证的嵌套用例计为顶层失败。真实 Kafka 和 XXL-JOB 联调入口已提供，本次没有目标环境凭据，未执行这两类联调。

## 2. 模块与依赖边界

```text
pom.xml                         Maven 聚合构建入口
testkit-parent/                  统一构建约束和插件/依赖版本
testkit-bom/                     可由公司已有父POM的业务仓库导入
base-test-toolkit/               核心：TestNG、HTTP、SDK、环境、数据库、任务契约
enterprise-adapters/             Dubbo、Kafka、XXL-JOB 适配器
java-api-lab/                    可本地部署的订单服务及框架实战
enterprise-team-example/         团队模式本地接入样例
config/environments.yaml         local/dev/qa 环境配置样例
verify-lifecycle.ps1             本地/CI完整闭环验收
verify-framework.ps1             模块报告检查兼容入口
```

核心继续基于 Java 17，使用 TestNG 7.10.2、Jackson 2.18.11；新增 JavaTime 支持，H2 2.3.232 仅用于数据库测试。适配器验证版本为 Dubbo 3.3.5、Kafka Java Client 3.9.2；这些是本次固定测试版本，不代表对任意版本的兼容承诺。

Dubbo/Kafka 依赖设为 optional。消费项目需要使用哪个中间件，就显式引入对应客户端；只使用核心时无需带入这些库。若在公司项目中改变客户端版本，应重新运行适配器契约测试和真实平台联调。

```xml
<dependency>
  <groupId>io.testkit</groupId><artifactId>base-test-toolkit</artifactId>
  <version>1.1.0-SNAPSHOT</version><scope>test</scope>
</dependency>
<dependency>
  <groupId>io.testkit</groupId><artifactId>enterprise-adapters</artifactId>
  <version>1.1.0-SNAPSHOT</version><scope>test</scope>
</dependency>
<!-- 使用 Dubbo/Kafka 时另按 enterprise-adapters/pom.xml 的版本声明其依赖 -->
```

根目录运行 `mvn install` 后构件进入本地仓库。它们没有发布到远程公共仓库；其他机器需要安装源码或使用公司的制品仓库。本轮使用1.1.0-SNAPSHOT开发版本，保留旧1.0.0构件，不覆盖已发布release。正式交付应经过企业门禁、试点与发布审批，再更新父POM/BOM及模块的精确版本。

## 3. 能力与验证矩阵

| 层级 | 实现入口 | 支持内容 | 本次证据与边界 |
|---|---|---|---|
| HTTP | http/ApiClient、ApiResponse | JSON/原文、显式身份、状态码、响应转换、超时、不跟随重定向、8 MiB 响应上限 | 本地真实 HTTP + 订单业务；未测企业 TLS/mTLS/代理 |
| Dubbo | adapters/dubbo/DubboClient | 泛化调用、接口/group/version、直连或注册中心、超时、0 业务重试、attachments、引用销毁 | 本地真实 Dubbo 网络调用；注册中心插件及服务端鉴权需环境验证 |
| SDK | sdk/SdkInvoker、config/TestClients | 类型安全 Callable、限线程/队列、超时取消、关闭取消运行和排队调用、拒绝日志、上下文传递 | SDK 本地调用、失败、超时、关闭、线程复用已测；忽略中断的 SDK 无法被强制停止 |
| 定时任务 | job/LocalJobService | 延迟调度、状态查询、失败、环境检查、容量限制、关闭待运行任务 | 本地真实执行；非分布式 cron 引擎 |
| XXL-JOB | adapters/xxljob/XxlJobService | 登录 Cookie、任务白名单、触发、日志关联、执行状态查询 | 2.4.1/3.1.1 官方管理端契约对应的本地 HTTP 测试；实际平台未联调 |
| 其他调度平台 | JobService、HttpJobService | 保留统一 trigger/query；显式测试网关 REST 协议 | 测试网关本地 HTTP 已测；不冒充任意平台原生 API |
| 消息发送 | adapters/kafka/KafkaPublisher | key/headers、runId、发送确认、幂等 producer、超时 | Kafka MockProducer；真实 broker 联调入口另列 |
| 消息观察 | MessageProbe、KafkaMessageProbe | 独立测试 group、先 ready 再触发、关联过滤、未读批保留、超时、禁止自动提交 | MockConsumer + 本地队列验证；不能仅以消息存在推断业务消费成功 |
| 消费处理 | KafkaConsumerRunner | 成功提交已处理 offset、异常回退、批内缓存、线程归属 | Kafka MockConsumer 验证；需实际 rebalance/重复投递验证 |
| 多环境 | config/EnvironmentConfig、TestClients | common/dev/qa/local、覆盖优先级、按能力选择配置、变量替换、HTTP/SDK 客户端创建 | 文件配置/覆盖/缺变量/脱敏及客户端实测 |
| 数据库 | database/JdbcSession | JDBC、PreparedStatement、查询时限/行数、事务、null 值、关闭回滚 | H2 实测；MySQL/Oracle/PostgreSQL 驱动及超时行为需分别验证 |
| 数据清理 | runtime/CleanupStack、BaseTest.deferCleanup | LIFO 补偿、继续清理其余资源、配置阶段失败传播 | 清理顺序及异常已测；业务数据跨进程必须显式删除 |
| Mock | mock/HttpMockEngine | 每场景独立端口、method/path/JSON body 匹配、次数校验、失败释放 | 真实 HTTP 已测；被测服务必须显式配置下游地址 |
| 并发 | RuntimeProvider、RuntimeRegistry.Scope | 测试实例专属运行时、线程隔离、LIFO、显式工作线程传播 | 两实例 × 四数据行、SDK worker 回归 |
| 数据驱动 | DataDriveUtil | YAML/JSON5、debugCases、enabled、JAR 相对资源、严格基本类型 | 原有数据及 JAR 实测；不是完整 JSON Schema 验证 |
| 断言 | JsonDiff、JsonContract、Await、JobAwait | strict/lenient、动态路径忽略、JSON Pointer 类型/必填/数组长度、异步状态轮询、任务终态断言 | 差异、结构、超时、失败立即停止、异常传播、中断已测；不是完整 JSON Schema |
| 可观测性 | TraceContext、InvocationRecord、ResultPublisher | runId/caseId 关联、HTTP/SDK/Dubbo 调用指标、JSONL、配置阶段失败、多路发布、汇总 | 用例与调用关联、初始化/清理失败、文件并发及报告回归；不是分布式追踪系统 |
| 构建与门禁 | Maven reactor、verify-framework.ps1 | 模块顺序构建、Surefire XML、失败退出、零执行防护 | 默认构建测试通过；未部署到公司的 CI |

## 4. dev / qa 环境如何工作

```java
EnvironmentConfig config = EnvironmentConfig.load(Path.of("config/environments.yaml"));
ApiClient http = new ApiClient(config.require("http.base.url"),
        Duration.ofMillis(config.positiveInt("http.timeout.ms")), report);
```

`-Dtest.env=qa` 或 `TEST_ENV=qa` 选择环境。值覆盖顺序：common < 选中的环境 < TEST_ATTR_* 环境变量 < test.attr.* JVM 属性。`${QA_HTTP_URL}` 等变量仅解析选中环境；配置对象的 toString 仅显示键名。

```powershell
$env:TEST_ENV = 'qa'
$env:QA_HTTP_URL = 'https://你的qa接口地址'
$env:QA_DUBBO_URL = 'dubbo://你的qa服务:20880'
$env:QA_KAFKA_BOOTSTRAP = '你的qa-broker:9092'
$env:QA_XXLJOB_URL = 'https://你的qa调度中心/xxl-job-admin/'
```

默认 load 解析选中环境的全部变量；也可用下方带 Set 的重载只选择需要的顶层能力。纯 HTTP 项目选择 Set.of("http") 后，不必配置 Dubbo/Kafka/XXL-JOB 变量。密钥、登录密码、SASL/SSL 配置从环境变量或公司密钥服务注入，不提交真实值。

配置没有全局自动扫描：测试项目调用 EnvironmentConfig，并用解析值或 TestClients 创建客户端。TestEnvironment 仅提供环境名/属性；设置 test.env 本身不会改写既有硬编码地址。TestClients 按需创建 HTTP 客户端和有界 SDK 执行器；SDK 使用 sdk.timeout.ms、sdk.concurrency、sdk.queue.capacity。Dubbo/Kafka/XXL-JOB 继续由接入项目显式创建适配器。

```java
var config = EnvironmentConfig.load(Path.of("config/environments.yaml"), "qa",
        System.getenv(), Map.of(), Set.of("http", "sdk"));
// 此重载显式给出 profile / 覆盖属性；需要 JVM 覆盖时传入对应属性 Map。
try (var clients = new TestClients(config, runtime)) {
    clients.http().request("GET", "/health", Map.of(), null).assertStatus(200);
    Order order = clients.callSdk("order.query", () -> yourSdk.query(orderId));
}
```

同一 JVM 测多个环境时，显式加载各 EnvironmentConfig，并创建各自的客户端/RuntimeProvider，避免运行中改系统属性。生产环境没有默认入口；是否允许生产只读探测应由接入项目的准入策略决定。

## 5. 通用生命周期与并行运行

```java
public class OrderTest extends BaseTest implements RuntimeProvider {
    private AutomationRuntime runtime; // @BeforeClass 中构造该实例自己的运行时
    @Override public AutomationRuntime automationRuntime() { return runtime; }

    @Test
    @UseIdentity("reviewer")
    public void createOrder() {
        // 测试体内可取 IdentityContext、RuntimeRegistry.current、TraceContext
        String id = createFixture();
        deferCleanup(() -> deleteFixture(id)); // 创建成功后立刻注册
        // 调用及断言
    }
}
```

beforeInvocation 绑定运行时、trace、身份和 Mock；afterInvocation 独立关闭 Mock、身份、归还账号及作用域。结果回调发布记录，execution finish 按参与发布器汇总。@AfterMethod 清理数据补偿栈及返回值上下文。身份默认不在 @BeforeMethod 阶段提供；需要身份的造数放在测试体或显式身份作用域。

框架 trace 是本地关联上下文；希望业务系统也记录 runId 时，显式在 HTTP header、Dubbo attachments 或消息 headers 中携带。SDKInvoker 显式复制运行时、身份和 trace 到工作线程；ReturnValueContext/CaseContext 不自动复制，输入数据应作为 Callable 参数捕获。

deferCleanup 属于 TestNG 配置清理阶段。监听器捕获 TestNG 配置失败，输出独立 FAILED 记录和 phase=configuration，纳入汇总；正常测试记录为 phase=test。一次测试体通过但清理失败，会有一条 PASSED 测试记录和一条 FAILED 配置记录，因此汇总数包含配置失败事件，不能视为业务测试方法数量。配置跳过不另输出事件，测试跳过仍记录。详细错误保留在 Surefire/TestNG；JSONL 不输出异常消息和敏感值。**门禁仍以 Maven 退出码和 Surefire 报告为准**。运行时尚未构造就失败时，只能使用已存在的兼容运行时发布器；若要持久化初始化失败，应尽早配置发布器。

RuntimeRegistry.install/reset 仍保留给串行兼容代码；类级并行建议使用 RuntimeProvider，不能让多个类并发修改全局运行时。订单实战 OrderApiTest 已使用 RuntimeProvider。客户端、数据库会话、Kafka consumer、Mock session 等仍遵守各自的线程约束。

## 6. 各层测试方式

### HTTP

`api.request(method, path, headers, body)` 用于明确控制认证的场景；`api.authenticated(...)` 读取当前身份。先断言 HTTP 状态，再断言业务字段，最后观察数据库或消息副作用。多环境客户端只访问配置的同一主机；HTTPS 使用 JDK 默认校验，不关闭证书验证。

当前主路径是 JSON、UTF-8 文本；multipart、二进制上传下载、SSE、WebSocket、mTLS 自定义客户端没有完整实现。不能用此处 8 MiB 限制直接承载大型导出测试。

JsonContract 用 JSON Pointer 检查必填字段、类型、非空字符串和数组长度。缺字段与显式 null 分开处理；错误不包含字段值。JsonDiff 继续负责期望值比较，两者可以组合：

```java
JsonContract.of(response.json()).nonBlank("/orderId")
        .required("/quantity", JsonContract.Type.INTEGER)
        .required("/totalPrice", JsonContract.Type.NUMBER);
// 如果返回分页列表：JsonContract.of(page.json()).arraySize("/items", 0, 100);
```

指针的 `/` 用 `~1` 转义、`~` 用 `~0` 转义；空字符串表示根节点。该工具没有实现 JSON Schema 的引用、组合和格式验证。

### Dubbo

```java
try (var rpc = new DubboClient("automation", "com.example.OrderService", "qa", "1.0",
        null, config.require("dubbo.url"), Duration.ofSeconds(3))) {
    Object response = rpc.invoke("queryOrder", new String[]{"java.lang.String"},
            new Object[]{orderId}, Map.of("testRunId", TraceContext.current().orElseThrow().runId()));
}
```

泛化调用无需业务接口 JAR；复杂参数用 Map 描述，具体类型表达遵循 Dubbo 协议。鉴权 attachment 的名字由业务服务规定，`authenticated(..., tokenAttachment)` 要显式传入。注册中心需要对应客户端/插件，当前网络验证使用直连。typed proxy、Triple streaming、公司过滤器需另做适配。

### SDK

```java
try (var invoker = new SdkInvoker(2, 20)) {
    Order actual = invoker.call("order-sdk.query", Duration.ofSeconds(3), () -> yourSdk.query(orderId));
}
```

SDK 由测试项目引入其真实 JAR，框架不伪造业务 SDK。Callable 保留类型和异常 cause；超时会请求取消，中断不一定能停止第三方 SDK，所以 SDK 自身网络连接也应设超时。close 会取消运行及排队 Future、唤醒等待调用，并拒绝新调用；取消、队列拒绝进入调用指标。取消并不撤销已发生的远程业务操作。不可自动重试非幂等创建/支付操作。

### XXL-JOB（本次优先实现）

```java
try (var jobs = new XxlJobService(config.require("xxljob.admin.url"), config.name(), groupId,
        Map.of("settlement", dedicatedTestJobId), XxlJobDialect.forVersion("2.4.1"), Duration.ofSeconds(10))) {
    jobs.login(System.getenv("TEST_XXLJOB_USERNAME"), System.getenv("TEST_XXLJOB_PASSWORD"));
    JobExecution execution = jobs.trigger(new JobRequest("order", "settlement", config.name(), null,
            Map.of("runId", UUID.randomUUID().toString())));
    JobExecution terminal = JobAwait.succeeded(jobs, execution.id(), Duration.ofSeconds(60), Duration.ofMillis(200));
    // 再检查业务订单或数据库，证明任务实际副作用符合预期
}
```

实现管理端 form 登录、Cookie、jobinfo/trigger、joblog/pageList；区分 triggerCode 和 handleCode。任务别名映射到已批准的 jobId，环境必须匹配，地址不覆盖 executor 的注册配置。

原生触发接口不直接返回本次 logId，因此适配器以触发前日志 ID 为基线，发现新增执行。需要**专用测试任务、关闭其自动 cron，并避免其他触发者和广播路由**；出现多个新日志会失败，不猜哪个属于本用例。查询当前限定最近 100 条记录；大量日志或日志被清理时返回 empty，不能当成功。

触发后查询超时代表结果未知，不会再次触发。close 仅清除适配器本地 Cookie/追踪记录，不会杀任务或撤销平台会话。cron 配置准确性、misfire、分片广播、路由和恢复能力属于真实平台专项测试。

JobAwait 可用于任意 JobService 实现：SUCCEEDED 返回；FAILED/CANCELLED 立即断言失败；empty/QUEUED/RUNNING/UNKNOWN 持续等待至超时；查询异常直接传播，查询返回其他执行 ID 直接报错。它不会触发或取消远端任务，也不会把“查不到执行”视为成功。

### XXL-JOB 版本扩展

XxlJobDialect 将路径、登录表单、Cookie 名、触发表单、日志分页参数和响应解析隔离。内置 legacy 明确接受 2.4.1、3.1.1；其契约测试使用同一类 HTTP 结构，不代表已经部署这两个版本验收。

新版本/SSO 网关实现 XxlJobDialect，并添加对应响应样例和 HTTP 契约测试，再运行真实平台联调。升级后的新 Cookie/envelope/字段不应静默回退到旧版。不同平台实现 JobService 即可；HttpJobService 对应约定的 `/test-jobs` 测试网关，而非原生 XXL-JOB。

### 消息与消费

KafkaMessageProbe 用独立随机 group，默认 latest，必须先 `ready(timeout)` 再发送触发请求。用 runId、业务 ID 或 key 精确匹配；批次中未读记录保留，已检查且不匹配记录丢弃，适合每个探针对应一条测试链路。

KafkaPublisher 等待发送确认，启用 Kafka producer 幂等；确认成功只证明发送成功。观察消息、调用真实 consumer handler、检查消费后的数据库分别是不同的证据。

KafkaConsumerRunner 处理成功才提交该 partition 的 offset+1，失败则回退未处理记录。自行注入 consumer 时必须关闭 auto-commit。提交失败可能已经处理成功但提交结果未知，业务 handler 仍需幂等；rebalance/至少一次投递语义不会因此消失。

消费者由创建线程独占，close 也在该线程执行。消息等待不自动创建 topic、不删除共享消息、不消费公司的业务 group。Kafka 属性支持传入 SASL/SSL 配置；认证组合本次未实测。RocketMQ/RabbitMQ 可实现 MessageProbe 和对应发送/消费适配器，目前未提供其具体实现。

## 7. 数据库、造数和 Mock

JdbcSession 接收 JDBC Connection，关闭时尝试 rollback 再 close，PreparedStatement 绑定参数。连接建立超时、驱动读超时及数据库权限由接入方连接配置负责。

同连接回滚不会回滚被测远程服务的事务。需给测试造数 commit 使其跨进程可见时，注册独立补偿删除；以 runId 前缀标记订单、账号或记录，清理时限定自己的数据。DataFactory 仍为业务造数 SPI，不生成通用公司业务数据。

HttpMockEngine 可通过 YAML mockId 查找场景规则，打开后取得 currentBaseUrl，测试项目或被测服务显式把下游地址指向该 URL。每次 session 关闭检查次数并释放监听端口。它是可控本地 HTTP stub，不是自动抓取/改写所有真实 HTTP 流量的代理，也不模拟 RPC/MQ 全协议。

内存 MockEngine 原实现继续保留，方便生命周期学习。JAR 测试数据支持相对 URL 且关闭缓存；强类型基本字段禁止字符串数字隐式转换。完整 JSON Schema、OpenAPI/Pact 契约、数据模板表达式需进一步选型实现。

## 8. 报告、保密与故障诊断

- 每次调用生成独立 caseId，报告含 runId、caseId；HTTP/SDK/Dubbo 调用指标可关联测试。Kafka/任务的业务关联通过 key/header/executorParams 明确传递。
- JsonLinesResultPublisher 省略 payload、目标地址、任意 metadata、异常文本；InvocationRecord/JobRequest toString 也省略载荷。
- Surefire/TestNG 仍可能展示参数和原始错误；真实密码不放进 DataProvider 参数、用例名或断言消息。
- 多路发布独立尝试各输出，初始化/清理异常保留原始异常并聚合 suppressed。
- 发布器以每个实例为粒度同步写入；不保证多进程共写同文件，CI 每个任务使用独立工作区或报告文件。
- 默认不做业务请求重试和失败用例自动重跑，避免把偶发失败掩盖为成功；需重跑时把首次失败证据单独保存。

## 9. 运行命令与真实平台入口

普通构建不会触发外部任务和消息平台：

```powershell
cd E:\AITest\test\baseTest
powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-framework.ps1
mvn install
```

四个测试模块均生成 Surefire XML/HTML；脚本默认拒绝任何跳过、失败/错误、缺失/陈旧报告及不一致的计数。`-ReportOnly` 验收已完成构建的报告；多worker另用 ci/verify-shards.ps1 验收完整计划及全部分片。CI应保存各模块target/surefire-reports及计划/JSONL，使用整个流水线状态门禁；root clean会清理各target报告。

真实 Kafka smoke 必要变量：TEST_ENV、TEST_KAFKA_BOOTSTRAP、TEST_KAFKA_TOPIC（预先创建的专用 topic）。运行：

```powershell
mvn -pl enterprise-adapters -am verify -Pkafka-live
```

真实 XXL-JOB smoke 必要变量：TEST_ENV、TEST_XXLJOB_URL、TEST_XXLJOB_GROUP_ID、TEST_XXLJOB_JOB_ID、TEST_XXLJOB_VERSION、TEST_XXLJOB_USERNAME、TEST_XXLJOB_PASSWORD。运行：

```powershell
mvn -pl enterprise-adapters -am verify -Pxxljob-live
```

显式启用的 live 测试缺配置直接失败，不会静默跳过伪装联调成功。一次选择一个 profile。XXL live 用例会实际触发专用任务，Kafka live 会实际写入专用 topic。当前 live 样例用于基础连通/任务完成；TLS/SASL 等高级配置按测试项目扩展 Properties。

## 10. 完成企业环境验收还需要什么

| 项目 | 当前状态 | 验收要求 |
|---|---|---|
| 实际 dev/qa 隔离 | 配置结构及本地隔离测试完成 | 各环境地址、账号、topic、group、jobId、数据库一致；保存联调报告 |
| XXL-JOB 调度业务 | 原生契约适配完成 | 实际版本认证、专用任务、失败/长任务/调度时间及业务结果验证 |
| Kafka 消费可靠性 | 客户端适配/offset 逻辑完成 | 实际 rebalance、重复、延迟、死信、异常恢复及幂等验证 |
| Dubbo 生产形态 | 直连泛化完成 | Nacos/ZK 插件、过滤器、鉴权附件、TLS、序列化和网络超时验证 |
| SDK 业务覆盖 | 调用基础设施完成 | 真实 SDK 版本、线程安全、错误码、网络与限流契约 |
| 数据治理 | JDBC/回滚/补偿基础设施完成 | 最小权限、并行数据唯一性、跨进程清理、保留周期 |
| 运行时质量 | 并行/异常回归完成 | 目标规模并发测试、拒绝中断任务处理、线程/连接泄漏观测 |
| 合规和供应链 | 依赖固定及 optional 边界 | 公司依赖漏洞/许可证扫描、制品签名、版本发布策略 |
| 覆盖率和平台报告 | SPI 保留 | JaCoCo 与测试管理/告警平台需要真实适配器 |
| 契约/非功能测试 | JSON 内容断言基础 | OpenAPI/JSON Schema、性能容量、混沌测试不属于已完成能力 |

这里的未完成项不影响已经实现的分层入口，但决定框架在你们企业环境能否正式验收。推荐下一步先接入 qa 的专用 XXL-JOB 任务并保存业务结果，再跑 Kafka 真 broker 和 Dubbo 注册中心场景。

## 11. 源码阅读入口

核心新增测试：EnterpriseCapabilitiesTest、RuntimeIsolationTest、HttpJobServiceTest、HttpMockEngineTest、TestClientsTest、SdkShutdownTest、ConfigurationReportTest、JsonContractTest、JobAwaitTest。既有 LifecycleRegressionTest 验证生命周期失败路径。适配器测试：DubboClientTest、KafkaAdaptersTest、XxlJobServiceTest。真实环境样例：KafkaLiveIT、XxlJobLiveIT。

阅读顺序：EnvironmentConfig/TestClients → RuntimeProvider/PortableTestNgListener → HTTP/SDK → JobService/JobAwait/XxlJobDialect → MessageProbe/KafkaConsumerRunner → JsonContract/JdbcSession/CleanupStack → 报告与 trace。

## 12. 本次核对的官方协议资料

- [Apache Dubbo 泛化调用](https://dubbo.apache.org/en/overview/mannual/java-sdk/tasks/framework/generic/)：泛化客户端的参数约定。
- [Apache Kafka 3.9 消费者 API](https://kafka.apache.org/39/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)：线程归属、poll、offset 提交。
- [Kafka MockConsumer](https://kafka.apache.org/39/javadoc/org/apache/kafka/clients/consumer/MockConsumer.html)：客户端级测试替身，不作为 broker 实测证据。
- [XXL-JOB 2.4.1 JobInfoController](https://github.com/xuxueli/xxl-job/blob/2.4.1/xxl-job-admin/src/main/java/com/xxl/job/admin/controller/JobInfoController.java)、[IndexController](https://github.com/xuxueli/xxl-job/blob/2.4.1/xxl-job-admin/src/main/java/com/xxl/job/admin/controller/IndexController.java)：管理端触发和登录。
- [XXL-JOB 2.4.1 JobLogController](https://github.com/xuxueli/xxl-job/blob/2.4.1/xxl-job-admin/src/main/java/com/xxl/job/admin/controller/JobLogController.java)、[3.1.1 JobLogController](https://github.com/xuxueli/xxl-job/blob/3.1.1/xxl-job-admin/src/main/java/com/xxl/job/admin/controller/JobLogController.java)：日志分页与状态字段。本文兼容范围是这两版旧式接口契约，不推断所有 3.x 版本相同。
