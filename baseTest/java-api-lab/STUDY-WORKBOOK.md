# TestNG 框架带练手册

最新企业能力入口：[能力审计与使用指南](../ENTERPRISE-CAPABILITIES.md)。框架现支持测试实例 RuntimeProvider 隔离，类级并行不要继续沿用本演示的全局 install/reset 方式。

目标是能独立写用例、追踪生命周期、定位失败并扩展框架。每课按“先预测 → 运行 → 断点追踪 → 修改 → 解释结果”进行。下面是可持续使用的学习路线；完成项目搭建不等于已经掌握框架。

## 第一课：看懂一条真实订单用例

先运行（工作区根目录）：

```powershell
mvn -f .\java-api-lab\pom.xml test "-Dtest=OrderApiTest"
```

预期 18 条通过；另一个 TokenExpiryTest 是第 19 条。只看 `createReadDelete` 方法和 `orders.yaml`，先不要逐个读框架所有类。

一条 `order-coupon` 数据经历以下过程：

1. TestNG 识别 `@Test(dataProvider="orders")`，调用 `orders(Method)`。
2. `DataDriveUtil.loadTestData` 根据测试类包名查找 `io/testkit/lab/orders.yaml`，筛掉 enabled=false，用反射读取方法参数类型。
3. `CaseContext` 来自 YAML 元数据；request 文件转换为 `CreateOrder`；expect 文件转换为 `JsonNode`。文件按 request 列表然后 expect 列表依次消费，顺序必须匹配方法参数。三条启用数据形成三行 `Object[]`。
4. `@BeforeClass setup` 安装本项目运行时。继承的 `BaseTest.bindBaseTestContext` 在每次调用前绑定当前用例。
5. 通过 JAR 内 `META-INF/services/org.testng.ITestNGListener` 自动发现的 `PortableTestNgListener`，在 `beforeInvocation` 读取 `@UseIdentity("alice")`。
6. `LoginIdentityProvider.acquire` 调用真实登录接口，把得到的 token 包成 `UserIdentity`，监听器再放入 `IdentityContext`。
7. 测试体取当前身份，通过本项目 `ApiClient` 发 HTTP 请求。注意：`BaseTest` 自己没有 HTTP 客户端，也不会自动添加请求头。
8. 先断言 HTTP 201，再用 `JsonDiff.strict` 比较 JSON。orderId 虽被忽略比较，仍单独检查非空，并用于 GET/DELETE 验证。
9. 测试体在 finally 删除自己的订单。`afterInvocation` 关闭身份作用域并调用 `release` 注销 token；成功/失败回调发布结果。`BaseTest` 的 `@AfterMethod(alwaysRun=true)` 清除用例上下文和返回值上下文。

这里 `ReturnValueContext` 只在同一个测试调用内保存 orderId；不同测试方法之间会清空，不能用它传递整个测试套件的数据。

### IDE 断点

将两个目录作为 Maven 项目导入 IDE，JDK 设为 17 或更高。确保能打开工具包源码（必要时附加 `base-test-toolkit/src/main/java`）。依次在这些位置下断点：

| 类/方法 | 看什么 |
|---|---|
| DataDriveUtil.toArguments | parameterTypes、payloads、payloadIndex |
| BaseTest.bindBaseTestContext | parameters 内的 CaseContext |
| PortableTestNgListener.beforeInvocation | 注解、运行时、获取的身份 |
| LoginIdentityProvider.acquire | profile 如何变为真实登录 |
| OrderApiTest.createReadDelete | context、request、expected、created |
| PortableTestNgListener.finish | status、结果发布、scope.close、release |
| BaseTest.clearBaseTestContext | 何时清空 ThreadLocal |

身份在 beforeInvocation 获取，不要假设自己写的 `@BeforeMethod` 中已经存在 `@UseIdentity` 身份；用断点确认真实执行顺序。

### 今天的动手任务

把 coupon-request.json5 的 quantity 从 2 改成 3，先不改期望：运行应失败，差异涉及 quantity 和 totalPrice（实际 280）。读报告后，把期望改成 quantity=3、totalPrice=280，再运行应通过。完成后可以恢复原始数据。

请用自己的话回答：

1. YAML 中一行用例怎样变成三个 Java 参数？
2. token 是谁获取、谁保存、谁加到 HTTP 头里的？
3. 为什么除了断言 201，还要比较响应内容？
4. 断言失败时，订单和 token 分别由谁清理？

这四个答案和失败报告就是下一轮一起分析的材料。

## 后续课程与验收

| 课次 | 源码入口（工具包 src/main/java/io/testkit/basetest） | 实验 | 验收 |
|---|---|---|---|
| 2 数据驱动 | data/DataDriveUtil、TestCaseData、CaseContext、config/ConfigLoader | 增加一组 JSON5 文件和 YAML；使用 debugCases；交换 request/expect 顺序 | 能解释文件路径、参数类型和过滤，定位“文件不存在/参数数目不匹配” |
| 3 断言 | assertion/JsonDiff、Difference、config/StrictTypeModule | 比较 strict/lenient；改变字段类型；增加动态字段 | 能解释差异路径，避免忽略过多字段导致假通过 |
| 4 鉴权 | identity/UseIdentity、IdentityProvider、UserIdentity、IdentityContext、InMemoryIdentityProvider | 新增 bob 用例、检查角色/归属权限、读过期测试 | 区分 401 和 403，解释本地账号池与真实登录适配器的区别 |
| 5 生命周期 | BaseTest、ReturnValueContext、runtime/PortableTestNgListener | 故意让断言失败，用断点确认 release 和清理仍执行 | 分清 TestNG 配置回调、测试回调和 finally |
| 6 环境配置 | config/TestEnvironment、ConnectionSpec、ConfigCatalog | 用 -Dtest.attr.base.url 指向手工服务；对比系统属性与环境变量 | 解释 JVM 属性优先；test.env 名称本身不会自动切换地址 |
| 7 并发隔离 | identity/IdentityContext、InMemoryIdentityProvider、data/CaseContextHolder | 在独立分支给 orders 的 DataProvider 开 parallel=true | 能验证身份、orderId 不串用；理解同账号多会话和账号池独占不同 |
| 8 Mock | mock/MockEngine、InMemoryMockEngine、MockSession、MockScope | 先跑原框架 InMemoryMockEngineTest；再设计下游库存接口和 HTTP Mock 适配器 | 明白内存 Mock 仅记录场景，mockId 不会自动替换网络响应 |
| 9 报告和日志 | runtime/ResultPublisher、InvocationLogSink、TestResultRecord、TestRunSummary | 实现 JSONL 发布器；在 ApiClient 主动记录脱敏调用日志 | 结果不丢、不记 token，区分单用例记录与运行汇总 |
| 10 高级扩展 | data/DataBuilderProcessor、DataFactory、coverage、job、discovery | 跑原框架处理器测试；任选一个 SPI 做真实适配 | 能说清已实现能力与接口契约，写出最小适配器及验证 |

每课只引入一个新因素。当前测试默认串行；`RuntimeRegistry` 是全局静态运行时，多测试类并行 install/reset 会互相影响，不要直接打开整套类级并行。

## 当前框架需要特别研究的边界

这些结论来自当前源码，不能只根据类名猜能力：

- YAML `users` 现支持单用户自动登录；显式 `@UseIdentity` 优先，多用户且无注解会明确报错。
- `mockId` 创建一个规则列表为空的场景；当前 InMemoryMockEngine 不拦截 HTTP。
- CoverageCollector、InvocationLogSink、JobService、ServiceDiscovery、DataFactory 是扩展契约；安装运行时不意味着全部被自动调用，需检查实际调用点。
- 本项目同时使用内存发布器和 JSONL 文件发布器，HTML/XML 报告来自 Maven Surefire/TestNG。
- 监听器现捕获每次调用的运行时并记住发布器；即使 `@AfterClass` reset，原发布器仍可收到其对应的汇总。
- 监听器现独立执行各清理步骤，初始化及清理失败有专项回归；清理失败会令测试失败并保留原异常。
- 数据加载现支持 file 和 JAR classpath 资源，并禁用 URL 缓存以释放 Windows 文件锁。
- 构建配置关闭自动注解处理（proc=none）；研究 DataBuilderProcessor 时结合原框架显式调用编译器的测试，不要以为加注解就一定生成代码。

## 故障定位顺序

1. Maven 编译/依赖错误：检查是否先 install 工具包、JDK 版本、仓库网络。
2. 连接拒绝：检查服务进程和 base.url；未指定地址的默认模式会自行启动服务。
3. DataProvider 报错：检查测试类包路径、YAML 根节点 testDataSuite、文件顺序和类型。
4. 401：检查登录响应、Bearer 头、注销时机、TTL；避免把 token 放进分享的日志。
5. 403：检查主体用户与角色、订单 owner；重新登录不会改变权限规则。
6. 断言差异：先看 HTTP 状态，再看 JsonDiff 路径，最后核对业务规则和数据文件。

## 最终掌握标准

不用复制示例，独立实现一个新接口及至少成功/参数错误/无身份/无权限四类测试；解释从 YAML 到报告的完整链路；证明失败清理有效；实现一个 SPI 适配器，并指出其生命周期与并发限制。
