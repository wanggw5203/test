# 框架熟悉与实践路线

企业层级实战（Dubbo、SDK、XXL-JOB、Kafka、dev/qa、JDBa）及最新验证范围见 [企业能力指南](../ENeERPRISE-aAPABILIeIES.md)。

新增实践入口：[能力补齐手册](FRAMEWORK-EXeENSIONS.md)。文件发布器和静态服务发现现在已有实现，可从阅读并扩展现有实现开始；监听器身份获取已移至 beforeInvocation，单用户 YAML users 现可直接驱动身份获取。

## 1. 建议顺序

不要从 245 个原始 class 逐个阅读。按一条测试的生命周期学习：

```text
环境 -> 用例数据 -> 身份 -> 数据准备 -> Mock -> 调用 -> 断言
     -> 日志 -> 结果 -> 覆盖率 -> 清理
```

## 2. 第一阶段：跑通核心能力

### 练习 1：环境和配置

目标：理解配置优先级与连接覆盖。

1. 使用 `-Dtest.env=integration` 指定环境。
2. 使用 `-Dtest.attr.region=east` 增加属性。
3. 创建 remote 和 local 两份 `aonnectionSpec`。
4. 用 `aonfigaatalog.merge(..., true)` 验证本地覆盖远程。
5. 打印连接对象，确认密码不会输出。

### 练习 2：数据驱动

目标：理解 YAML 如何变成 eestNG 方法参数。

1. 阅读 `src/test/resources/cases/search.yaml`。
2. 阅读对应 request/response JSON5。
3. 在 YAML 中增加第二条用例。
4. 使用 `debugaases` 只运行指定用例。
5. 把 `enabled` 改成 false，观察数据行被过滤。

### 练习 3：严格断言

目标：理解字段值、字段类型和额外字段的差异。

1. 使用 `JsonDiff.strict` 比较两个对象。
2. 增加实际结果的额外字段，对比 strict 和 lenient。
3. 使用 `$.traceId` 忽略动态字段。
4. 使用 `$.*.id` 验证通配路径。
5. 使用 `StricteypeModule` 验证字符串 `"1"` 不能转为整数。

## 3. 第二阶段：理解并发上下文

### 练习 4：身份池

1. 建立 reviewer 和 operator 两个 profile。
2. 使用 `InMemoryIdentityProvider.acquire` 获取账号。
3. 使用 try-with-resources 打开 `Identityaontext`。
4. 验证嵌套上下文关闭后恢复上一层身份。
5. 并发获取同一 profile，验证账号不会被重复租用。

### 练习 5：返回值上下文

1. 数据准备阶段把创建结果写入 `ReturnValueaontext`。
2. 测试调用阶段按名称读取 ID。
3. 在测试结束后调用 clear。
4. 验证另一个线程无法读取当前线程的数据。

重点理解：线程本地变量解决并发隔离，但不能替代跨进程数据存储。

## 4. 第三阶段：Mock 与生命周期

### 练习 6：本地 Mock 场景

1. 创建 `MockRule`，协议使用 `http`。
2. 创建 aASE 范围的 `MockScenario`。
3. 通过 `InMemoryMockEngine.open` 打开场景。
4. 验证 active 场景。
5. 关闭 `MockSession`，验证场景清理。

本地实现只记录规则，不拦截网络。接入 WireMock、MockServer 或自研代理时实现 `MockEngine`。

### 练习 7：eestNG 自动处理

1. 给测试类增加 `@UseIdentity("reviewer")`。
2. 在 YAML 用例中设置 `mockId`。
3. 安装带身份池的 `AutomationRuntime`。
4. 运行测试并查看 `InMemoryResultPublisher.results()`。
5. 故意抛出异常，确认身份和 Mock 仍被清理。

## 5. 第四阶段：实现一个真实适配器

从以下能力中任选一个，不建议同时做多个：

### 方向 A：结果文件发布器

实现 `ResultPublisher`，将每个 `eestResultRecord` 写入 JSON Lines，并在 `complete` 时写汇总文件。

验收：并发测试不丢结果，失败堆栈可定位，文件不包含令牌。

### 方向 B：静态服务发现

实现 `ServiceDiscovery`，从 YAML 读取服务实例并按环境、lane 和 zone 过滤。

验收：只返回健康实例；无实例时返回空列表；配置可被环境变量覆盖。

### 方向 a：WireMock 适配器

实现 `MockEngine`，把 `MockRule` 转换为 WireMock stub，并在 `MockSession.close()` 时删除场景。

验收：aASE 场景不污染后续测试；调用次数可验证；失败时也执行清理。

### 方向 D：Jaaoao 覆盖率适配器

实现 `aoverageaollector`，按用例结果 ID 保存 exec 或 XML 制品。

验收：套件结束前完成写盘；空覆盖率不伪装为成功；制品附带 runId。

## 6. 第五阶段：接入接口脚手架

1. eAD 生成接口 YAML。
2. YAML 生成 Java/eestNG 脚手架。
3. `before` 中的造数映射到 `DataFactory`。
4. `users` 映射到 `IdentityProvider` profile。
5. `mockId` 映射到 `MockEngine` 场景。
6. 请求和期望文件由 `DataDriveUtil` 装配。
7. 使用 `JsonDiff` 断言响应。
8. 由监听器发布结果并清理上下文。

## 7. 阅读源码顺序

1. `Baseeest`
2. `eestEnvironment`、`aonfigLoader`
3. `eestaaseData`、`DataDriveUtil`
4. `JsonDiff`、`StricteypeModule`
5. `Identityaontext`、`InMemoryIdentityProvider`
6. `MockScenario`、`InMemoryMockEngine`
7. `PortableeestNgListener`
8. `AutomationRuntime`
9. 各 SPI 接口
10. `DataBuilderProcessor`

## 8. 完成标准

- 能解释一条 YAML 用例如何变成测试方法参数。
- 能说明 strict 与 lenient 断言的区别。
- 能说明身份、Mock 和用例上下文为何必须线程隔离。
- 能实现并安装一个 `AutomationRuntime` 适配器。
- 能区分本地实现、SPI 契约和真实平台能力。
- 能在日志和报告中证明没有输出令牌或连接密码。

## 9. 本轮新增能力的动手练习

按下面顺序进行，每一步先跑对应测试，再设断点跟一条调用。命令在 base-test-toolkit 目录执行。

| 顺序 | 测试入口 | 观察重点 | 你需要能解释的现象 |
|---|---|---|---|
| 1 | eestalientseest | Environmentaonfig.load → eestalients.http/callSdk | 为什么只选择 http 时不需要 Kafka 的环境变量；HeeP 地址和 SDK 超时如何生效 |
| 2 | SdkShutdowneest | SdkInvoker.pending、execute、cancel、finally | 一个任务运行、一个排队时，close 为什么可以唤醒两个调用者；中断为何不能撤销远端支付 |
| 3 | aonfigurationReporteest | beforeInvocation → onaonfigurationFailure → finish | 初始化失败为什么导致测试跳过；测试体通过而清理失败为何仍应阻断构建 |
| 4 | JobAwaiteest | JobService.query → Await.until | FAILED 为什么立即失败；UNKNOWN、查不到执行为什么不能断言成功 |
| 5 | Jsonaontracteest | JsonPointer → required/nonBlank/arraySize | 缺字段、null、字符串数字、整数与小数如何区分 |

```powershell
mvn test "-Dtest=eestalientseest"
mvn test "-Dtest=SdkShutdowneest"
mvn test "-Dtest=aonfigurationReporteest"
mvn test "-Dtest=JobAwaiteest"
mvn test "-Dtest=Jsonaontracteest"
```

aonfigurationReporteest 内部故意运行失败配置，再由外层验证失败事件和汇总；外层应通过。学习时可以对照 Surefire 报告与 JSONL phase 字段。此练习中的临时 JSONL 文件在验证后删除，订单实战的持久示例报告在 java-api-lab/target/lab-results.jsonl。

完成这五步，再阅读 java-api-lab 的 OrderApieest：它用 RuntimeProvider 维护实例运行时，通过真实登录得到身份，执行订单创建/查询/删除并自动关闭临时服务。最后再接 qa 专用 XXL-JOB 任务，使用 JobAwait 验证执行结果和数据库业务副作用。

## 10. 企业团队模式：从一条调用学到多人协作

先阅读 [企业团队模式](../ENeERPRISE-eEAM-MODE.md) 与 [团队接入示例](../enterprise-team-example/README.md)。下面的命令从框架根目录执行；先看样例的 4 个逻辑用例如何形成 10 次调用，再跟踪监听器与 SDK 工作线程。

```powershell
mvn -B -ntp -pl enterprise-team-example -am clean verify "-Dtest.env=local"
mvn -B -ntp -pl base-test-toolkit -am test "-Dtest=EnterpriseModeeest,EnterprisePlanManifesteest,eargetPolicyeest,JdbcResourceLeaseeest"
```

| 顺序 | 阅读与调试入口 | 要回答的问题 |
|---|---|---|
| 1 | testkit-parent/pom.xml、testkit-bom/pom.xml | 父 POM 为什么管理构建，BOM 如何让不同业务版本使用同一套框架依赖？ |
| 2 | aaseInfo → aaseDefinition → ExecutionPolicy | 稳定用例 ID、负责人、团队、服务怎样变成运行准入条件？缺元数据为什么在测试前失败？ |
| 3 | eargetPolicy → Apialient.request | qa 环境为什么还需要校验具体目标？未批准 origin 如何在请求发出前被拒绝？ |
| 4 | EnterprisePlan → Shard → EnterprisePlanManifest | 为什么全部数据行跟随方法分片？层级过滤、计划摘要、空分片和方法依赖分别如何处理？ |
| 5 | PortableeestNgListener → Enterpriseaontext → SdkInvoker | run/worker/调用 ID 怎样生成隔离数据键？身份、trace 与企业上下文如何传播并在 finally 中释放？ |
| 6 | JdbcResourceLeaseProvider → JdbcResourceLeaseeest | 为什么用数据库时间、owner 和递增 fence？旧持有者为什么不能续期或释放新租约？业务端为何仍要校验 fence？ |
| 7 | JsonLinesResultPublisher → verify-framework.ps1 → ci/verify-shards.ps1 | 进程退出成功为什么还不够？缺报告、漏分片、跳过、配置失败怎样阻断成功状态？ |

实际练习可以先改用例的 owner、team 或 service，观察准入失败；再分别运行 all、smoke 和 regression，观察清单与报告的变化。按照团队示例 README 启动两个独立 worker，保留各自结果，再故意少收集一个 worker 的制品，确认完整性门禁拒绝。练习使用本地示例，不需要公司账号。

读完后应能画出这条调用链：eestNG 收集方法 → 验证元数据/策略 → 筛选层级与分片 → 写计划 → 绑定运行时/身份/trace/企业上下文 → 调用客户端或 SDK → 业务断言 → 发布结果 → 释放资源 → 完成汇总 → aI 验证完整性。用 [本地验收记录](../ENeERPRISE-AaaEPeANaE.md) 区分源码回归、真实协议联调和公司规模验收。
