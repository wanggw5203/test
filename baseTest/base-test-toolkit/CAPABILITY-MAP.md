# 能力映射表

最新实现状态见 [企业能力指南](../ENTERPRISE-CAPABILITIES.md)：新增独立协议适配器模块、实际环境配置加载、SDK、JDBC、真实 HTTP Mock 和并行运行时隔离。本文下面的原始 SPI 映射用于设计追溯。

2026-10-15闭环与规范审查：新增规范注解及兼容迁移、生命周期和未知结果修复、统一格式检查、含test scope的SBOM、源码/报告/制品证据。最新全量123次测试和81个门禁场景通过；节点审计、命名标准、前沿路线图统一由 [框架首页](../README.md) 进入。下面的93/46等数字均为历史记录。

2026-10-13 企业团队模式：新增统一父 POM/BOM、用例所有权、环境和 HTTP 目标准入、严格企业入口、稳定分片及完整性门禁、调用上下文和资源命名隔离、JDBC 资源租约、CI 与发布治理。最新全量为 93 次顶层测试（核心 56、适配器 8、订单演示 19、团队示例 10），均无失败、错误和跳过。详见 [100人团队模式](../ENTERPRISE-TEAM-MODE.md) 和 [本地验收记录](../ENTERPRISE-ACCEPTANCE.md)。下面的 46 条数字是上一轮的历史结果。

2026-10-13 继续补齐：TestClients 配置驱动 HTTP/SDK、按能力加载环境变量、SDK 关闭取消与拒绝指标、配置阶段失败 JSONL、JobAwait 任务终态、JsonContract 结构断言。核心 46 条、适配器 8 条、订单演示 19 条顶层测试通过；实际中间件联调边界以企业能力指南为准。

2026-10-08 补齐：HTTP 客户端、JSONL 文件报告、多路发布、异常清理、单用户 YAML 身份、JAR 数据加载、轮询、静态服务发现已实现。详细状态及使用方式见 [FRAMEWORK-EXTENSIONS.md](FRAMEWORK-EXTENSIONS.md)。以下原始能力映射中的远程平台/代理 SPI 仍需要外部实现。

## 1. 映射状态

- **已实现**：通用 JAR 内有可直接运行的实现。
- **本地实现**：可离线学习和测试，不拦截真实流量。
- **SPI**：保留完整职责边界，需要接入方实现。
- **不迁移**：组织私有协议或高风险敏感逻辑，不进入通用 JAR。

## 2. 原框架到通用框架

1. Spring TestNG 基类
   - 通用能力：`BaseTest`
   - 状态：已实现
   - 差异：不强制启动 Spring 容器。

2. 多 ObjectMapper 配置
   - 通用能力：`ConfigLoader`、`StrictTypeModule`
   - 状态：已实现
   - 支持：YAML、JSON、JSON5、严格类型。

3. 环境、分支、泳道、区域
   - 通用能力：`TestEnvironment`
   - 状态：已实现
   - 差异：环境属性自由扩展，不内置组织环境枚举。

4. 远程与本地中间件配置合并
   - 通用能力：`ConnectionSpec`、`ConfigCatalog`
   - 状态：已实现
   - 差异：不访问任何私有配置中心。

5. 动态密钥和加密配置服务
   - 通用能力：由外部配置源返回授权后的 `ConnectionSpec`
   - 状态：SPI
   - 原私有密钥协议：不迁移。

6. YAML 数据驱动
   - 通用能力：`DataDriveUtil`、`TestDataSuite`、`TestCaseData`
   - 状态：已实现

7. 数据构造注解和编译期适配器
   - 通用能力：`@DataBuilder`、`DataBuilderProcessor`
   - 状态：已实现

8. 返回值和命名数据传递
   - 通用能力：`ReturnValueContext`
   - 状态：已实现

9. 严格/宽松 JSON 比较
   - 通用能力：`JsonDiff`、`Difference`
   - 状态：已实现

10. 多端账号、随机账号和账号锁
    - 通用能力：`IdentityProvider`、`InMemoryIdentityProvider`
    - 状态：本地实现 + SPI

11. 用户线程上下文
    - 通用能力：`IdentityContext`、`@UseIdentity`
    - 状态：已实现

12. HTTP/RPC/本地 Mock
    - 通用能力：`MockEngine`、`MockScenario`、`MockRule`、`MockSession`
    - 状态：本地实现 + SPI

13. CASE/THREAD Mock 隔离
    - 通用能力：`MockScope`
    - 状态：契约已实现；真实代理隔离由 Mock 适配器完成。

14. TestNG 用例注册和生命周期
    - 通用能力：`PortableTestNgListener`
    - 状态：已实现
    - 差异：默认只在本地发布，不访问远程平台。

15. 远程结果上报和测试结束通知
    - 通用能力：`ResultPublisher`、`TestRunSummary`
    - 状态：本地实现 + SPI

16. HTML 报告
    - 通用能力：通过 `ResultPublisher` 扩展
    - 状态：SPI
    - 建议：接入 Allure 或实现独立 HTML Publisher。

17. 调用日志收集和批量保存
    - 通用能力：`InvocationRecord`、`InvocationLogSink`
    - 状态：SPI

18. 用例级覆盖率抓取
    - 通用能力：`CoverageCollector`、`CoverageRequest`、`CoverageArtifact`
    - 状态：SPI

19. 任务触发和任务日志查询
    - 通用能力：`JobService`、`JobRequest`、`JobExecution`
    - 状态：SPI

20. 服务治理、实例发现和健康过滤
    - 通用能力：`ServiceDiscovery`、`ServiceQuery`、`ServiceInstance`
    - 状态：SPI

21. 远程数据工厂
    - 通用能力：`DataFactory`、`DataPreparationRequest`
    - 状态：SPI

22. 分页模型
    - 通用能力：`PageRequest`、`PageResult`
    - 状态：已实现

23. 平台响应包装
    - 通用能力：由目标项目定义自己的 API 响应 DTO
    - 状态：不迁移

24. 内部认证、网关令牌和员工账号
    - 通用能力：`IdentityProvider`
    - 状态：SPI
    - 原协议和默认账号：不迁移。

25. 内部域名、环境和 Host 模板
    - 通用能力：`TestEnvironment` + `ServiceDiscovery`
    - 状态：不迁移原值。

## 3. 组合入口

所有能力通过 `AutomationRuntime` 组合：

```java
AutomationRuntime runtime = new AutomationRuntime(
        identityProvider,
        mockEngine,
        resultPublisher,
        coverageCollector,
        invocationLogSink,
        jobService,
        serviceDiscovery,
        dataFactory
);
RuntimeRegistry.install(runtime);
```

未配置的远程能力要么返回空结果，要么明确抛出 `UnsupportedOperationException`，不会静默访问未知平台。
