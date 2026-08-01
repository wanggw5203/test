# Base Test Toolkit

从综合自动化测试基础库中重新设计出的通用 Java/TestNG 能力包。代码只使用公开 Maven 依赖，不包含公司域名、内部包名、账号、环境枚举或私有平台协议。

## 文档入口

- [框架设计分析](ANALYSIS.md)：原 JAR 的架构、完整能力和设计取舍。
- [能力映射表](CAPABILITY-MAP.md)：原能力对应到通用类、SPI 或不迁移项。
- [学习实践路线](LEARNING-GUIDE.md)：按测试生命周期逐步熟悉框架。

## 已实现能力

- YAML、JSON、JSON5 配置与测试数据加载。
- 严格基础类型反序列化。
- TestNG 强类型数据驱动。
- 严格/宽松 JSON 比较和忽略路径。
- 环境属性和连接配置合并。
- 线程隔离身份上下文及本地账号池。
- Mock 场景、规则和生命周期模型。
- 测试结果、调用日志、运行汇总模型。
- TestNG 身份、Mock、结果和清理监听器。
- `@DataBuilder` 编译期 TestNG 适配器。
- 分页、返回值和用例上下文模型。

## 扩展接口

- `IdentityProvider`：账号池或认证。
- `MockEngine`：HTTP、RPC 或本地代理 Mock。
- `ResultPublisher`：报告或测试平台。
- `InvocationLogSink`：调用日志。
- `CoverageCollector`：用例级覆盖率。
- `JobService`：任务调度。
- `ServiceDiscovery`：服务实例发现。
- `DataFactory`：运行时造数。

## 构建

要求 JDK 17+ 和 Maven 3.9+：

```bash
./build.sh
```

或：

```bash
mvn clean package
```

输出：

- Maven 构建目录：`target/base-test-toolkit-1.0.0.jar`
- 稳定交付目录：`../dist/base-test-toolkit-1.0.0.jar`
- 校验文件：`../dist/base-test-toolkit-1.0.0.jar.sha256`

## 数据驱动示例

```java
@DataProvider(name = "cases")
public Object[][] cases(Method method) {
    return DataDriveUtil.loadTestData(method, "search");
}

@Test(dataProvider = "cases")
public void search(CaseContext context, SearchRequest request, SearchResponse expected) {
    SearchResponse actual = client.search(request);
    Assert.assertTrue(JsonDiff.strict(expected, actual).isEmpty());
}
```

默认从测试类包路径读取 `<suiteName>.yaml`，请求和预期文件相对 YAML 所在目录解析。

## 运行时组合

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

不安装外部适配器时，框架保持离线，不会访问网络或上传结果。

## 配置覆盖

- `-Dtest.env=integration` 或 `TEST_ENV=integration`。
- `-Dtest.attr.region=east` 或 `TEST_ATTR_REGION=east`。
- JVM 系统属性优先于环境变量。

## 安全边界

- `UserIdentity` 不输出 access token。
- `ConnectionSpec` 不输出连接属性。
- 通用 JAR 不包含任何默认账号、内部地址或私有密钥协议。
- 真实认证、配置解密和平台上报必须由外部适配器显式提供。
