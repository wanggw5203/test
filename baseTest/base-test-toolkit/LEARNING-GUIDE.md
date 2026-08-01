# 框架熟悉与实践路线

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
3. 创建 remote 和 local 两份 `ConnectionSpec`。
4. 用 `ConfigCatalog.merge(..., true)` 验证本地覆盖远程。
5. 打印连接对象，确认密码不会输出。

### 练习 2：数据驱动

目标：理解 YAML 如何变成 TestNG 方法参数。

1. 阅读 `src/test/resources/cases/search.yaml`。
2. 阅读对应 request/response JSON5。
3. 在 YAML 中增加第二条用例。
4. 使用 `debugCases` 只运行指定用例。
5. 把 `enabled` 改成 false，观察数据行被过滤。

### 练习 3：严格断言

目标：理解字段值、字段类型和额外字段的差异。

1. 使用 `JsonDiff.strict` 比较两个对象。
2. 增加实际结果的额外字段，对比 strict 和 lenient。
3. 使用 `$.traceId` 忽略动态字段。
4. 使用 `$.*.id` 验证通配路径。
5. 使用 `StrictTypeModule` 验证字符串 `"1"` 不能转为整数。

## 3. 第二阶段：理解并发上下文

### 练习 4：身份池

1. 建立 reviewer 和 operator 两个 profile。
2. 使用 `InMemoryIdentityProvider.acquire` 获取账号。
3. 使用 try-with-resources 打开 `IdentityContext`。
4. 验证嵌套上下文关闭后恢复上一层身份。
5. 并发获取同一 profile，验证账号不会被重复租用。

### 练习 5：返回值上下文

1. 数据准备阶段把创建结果写入 `ReturnValueContext`。
2. 测试调用阶段按名称读取 ID。
3. 在测试结束后调用 clear。
4. 验证另一个线程无法读取当前线程的数据。

重点理解：线程本地变量解决并发隔离，但不能替代跨进程数据存储。

## 4. 第三阶段：Mock 与生命周期

### 练习 6：本地 Mock 场景

1. 创建 `MockRule`，协议使用 `http`。
2. 创建 CASE 范围的 `MockScenario`。
3. 通过 `InMemoryMockEngine.open` 打开场景。
4. 验证 active 场景。
5. 关闭 `MockSession`，验证场景清理。

本地实现只记录规则，不拦截网络。接入 WireMock、MockServer 或自研代理时实现 `MockEngine`。

### 练习 7：TestNG 自动处理

1. 给测试类增加 `@UseIdentity("reviewer")`。
2. 在 YAML 用例中设置 `mockId`。
3. 安装带身份池的 `AutomationRuntime`。
4. 运行测试并查看 `InMemoryResultPublisher.results()`。
5. 故意抛出异常，确认身份和 Mock 仍被清理。

## 5. 第四阶段：实现一个真实适配器

从以下能力中任选一个，不建议同时做多个：

### 方向 A：结果文件发布器

实现 `ResultPublisher`，将每个 `TestResultRecord` 写入 JSON Lines，并在 `complete` 时写汇总文件。

验收：并发测试不丢结果，失败堆栈可定位，文件不包含令牌。

### 方向 B：静态服务发现

实现 `ServiceDiscovery`，从 YAML 读取服务实例并按环境、lane 和 zone 过滤。

验收：只返回健康实例；无实例时返回空列表；配置可被环境变量覆盖。

### 方向 C：WireMock 适配器

实现 `MockEngine`，把 `MockRule` 转换为 WireMock stub，并在 `MockSession.close()` 时删除场景。

验收：CASE 场景不污染后续测试；调用次数可验证；失败时也执行清理。

### 方向 D：JaCoCo 覆盖率适配器

实现 `CoverageCollector`，按用例结果 ID 保存 exec 或 XML 制品。

验收：套件结束前完成写盘；空覆盖率不伪装为成功；制品附带 runId。

## 6. 第五阶段：接入接口脚手架

1. TAD 生成接口 YAML。
2. YAML 生成 Java/TestNG 脚手架。
3. `before` 中的造数映射到 `DataFactory`。
4. `users` 映射到 `IdentityProvider` profile。
5. `mockId` 映射到 `MockEngine` 场景。
6. 请求和期望文件由 `DataDriveUtil` 装配。
7. 使用 `JsonDiff` 断言响应。
8. 由监听器发布结果并清理上下文。

## 7. 阅读源码顺序

1. `BaseTest`
2. `TestEnvironment`、`ConfigLoader`
3. `TestCaseData`、`DataDriveUtil`
4. `JsonDiff`、`StrictTypeModule`
5. `IdentityContext`、`InMemoryIdentityProvider`
6. `MockScenario`、`InMemoryMockEngine`
7. `PortableTestNgListener`
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
