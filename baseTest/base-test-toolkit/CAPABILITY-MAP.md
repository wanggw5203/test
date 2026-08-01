# 能力映射表

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
