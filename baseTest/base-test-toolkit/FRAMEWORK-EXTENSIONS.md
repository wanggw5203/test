# 框架能力补齐（2026-10-08）

本文为上一轮能力说明。后续 Dubbo、SDK、XXL-JOB、Kafka、多环境、数据库和 HTTP Mock 已补齐，最新状态及验证边界以 [企业能力指南](../ENTERPRISE-CAPABILITIES.md) 为准；本页末尾测试数量为上一轮历史记录。

本次围绕真实接口自动化补齐可运行实现；不增加新的 Maven 第三方依赖，继续使用 JDK 17、TestNG 和 Jackson。

## 能力清单

| 能力 | 实现入口 | 验证 |
|---|---|---|
| HTTP 客户端 | http/ApiClient、ApiResponse | 真实本地 HTTP、身份头、状态码、JSON、超时、禁止重定向和跨主机请求 |
| 文件报告 | runtime/JsonLinesResultPublisher | 多线程 40 次写入，每行均可解析；调用日志不落请求/响应正文 |
| 多路报告 | runtime/CompositeResultPublisher | 一路失败仍尝试其余发布器，并聚合异常 |
| 生命周期 | runtime/PortableTestNgListener | 并行数据行、初始化失败、Mock 关闭失败、身份归还失败、断言失败、发布失败、跳过 |
| 单用户 YAML 身份 | PortableTestNgListener.beforeInvocation | 无注解时消费 users 中的单个 profile |
| JAR 数据资源 | data/DataDriveUtil | 在临时 JAR 内读取 YAML 和相对 JSON；关闭后可删除 JAR |
| 异步断言轮询 | assertion/Await | 达成条件、超时、异常立即传播、中断标记保留 |
| 静态服务发现 | discovery/StaticServiceDiscovery | 健康状态和 service/environment/lane/zone 过滤 |

## HTTP 使用

```java
ApiClient api = new ApiClient("http://127.0.0.1:8080");
api.request("GET", "/health", Map.of(), null).assertStatus(200);

// 测试方法标注 @UseIdentity 后，在测试体内调用：
JsonNode order = api.authenticated("POST", "/api/orders",
        Map.of("productId", "BOOK", "quantity", 2))
        .assertStatus(201).json();
```

`authenticated` 显式读取当前线程的 IdentityContext；普通 request 不自动附加身份，方便测未登录场景。body 为 String 时作为原文发送；其他对象序列化为 JSON；null 不发送正文。响应支持 `.body()`、`.json()`、`.as(类型.class)`；空响应使用 body，不要强行解析 JSON。

路径按 URI.resolve 规则解析：`/api/orders` 从服务器根路径开始，`orders` 相对 base URL 的目录；带目录的 base URL 建议以 `/` 结尾。可直接在 path 中提供已编码 query。禁止绝对请求 URL 和重定向，避免携带身份时误发给其他主机。没有自动重试，以免重复创建订单。

连接和请求超时通过构造函数传入 Duration。日志适配器异常会使调用失败；若传输也失败，保留传输异常并把日志异常放入 suppressed。

## 报告与运行汇总

```java
var file = new JsonLinesResultPublisher(Path.of("target", "automation.jsonl"));
var memory = new InMemoryResultPublisher();
RuntimeRegistry.install(new AutomationRuntime(identityProvider, null,
        new CompositeResultPublisher(memory, file), null, file, null, null, null));
var api = new ApiClient("http://127.0.0.1:8080", Duration.ofSeconds(10), file);
```

同一个文件由同一个 JsonLinesResultPublisher 实例写入；同步 append 保证该实例的线程安全，不承诺多进程或多个实例共享文件的写入原子性。每次运行追加记录，通过 runId 区分；mvn clean 会删除 target 内旧报告。

行类型：test（单次执行）、invocation（调用指标）、summary（汇总）。日期写为 ISO 字符串，不需要 Jackson JavaTime 模块。

文件发布器省略异常文本、任意 metadata、请求/响应内容和目标地址，只保留结构化指标；test 的 name/caseId 来自用例，请勿把密码等放进用例名称。原始错误仍可能存在于 TestNG/Surefire 报告，不要在断言消息或数据参数中使用真实秘密。

监听器按发布器对象分别统计其收到的用例；多个发布器对应各自的汇总，不会把别的运行时的用例算进去。订单练习的 JSONL 统计 OrderApiTest 的 18 次执行，独立 TokenExpiryTest 使用默认运行时，因此 Surefire 总数为 19。

## 生命周期变化（阅读旧教程时注意）

1. 获取身份和打开 Mock 从 onTestStart 移到 **beforeInvocation（仅测试方法）**，初始化异常会使测试失败并清理已获取的资源。不是在 @BeforeMethod 前建立身份。
2. **afterInvocation** 中独立尝试 Mock 关闭、身份作用域关闭、身份归还。清理失败会使测试失败，原始断言异常保留，新增异常进入 suppressed。
3. 成功/失败/跳过回调发布最终记录；为每次调用生成唯一 caseId，同名 YAML 和并行执行不会重复 ID。name 仍可用于展示。
4. 每次调用捕获自己的运行时，清理和上报不受随后 RuntimeRegistry.reset 影响；运行结束给曾参与的发布器 complete。
5. @UseIdentity 优先于 YAML users；无注解时支持单个 user；多个 users 且无注解会明确报错，不静默选择。

RuntimeRegistry 依然是全局注册表：应在套件或类启动前安装稳定运行时。支持同一个运行时下的数据行并行，不支持多个并行类随意替换全局运行时。HTTP 超时已验证；TestNG 强制超时、线程被外部终止、拒绝中断的业务代码，不作为已验证的清理保证。

## 轮询与服务发现

```java
Order value = Await.until("订单变为已支付", Duration.ofSeconds(5), Duration.ofMillis(100),
        () -> queryOrder(), order -> order.paid());

var discovery = new StaticServiceDiscovery(instances);
var healthy = discovery.discover(new ServiceQuery("orders", "local", null, "east"));
```

轮询只用于读取状态；supplier 本身需设置超时，Await 不会强行中断阻塞调用。supplier 异常直接失败，不会吞掉错误重试。服务实例 metadata 使用 service、environment、lane、zone，查询的 null/空白字段表示不限。

## 验证与后续边界

```powershell
mvn -f .\base-test-toolkit\pom.xml clean install
mvn -f .\java-api-lab\pom.xml test
```

框架包含 27 条顶层测试，生命周期回归还在嵌套 TestNG 中运行 8 组×2 条并行数据；故意失败的嵌套用例由外层断言验证，不应误当成框架测试失败。订单项目 19 条测试验证真实业务兼容性。

尚未实现：HTTP Mock 代理、数据库造数/回滚、RPC 客户端、JaCoCo 采集、任务调度平台、配置中心、分布式账号池。已有 SPI 保留，不宣称这些外部能力开箱可用。下一轮扩展应按真实项目依赖选择，并配套集成测试。
