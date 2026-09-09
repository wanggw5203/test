# Base Test Toolkit

从综合自动化测试基础库中重新设计出的通用 Java/TestNG 能力包。代码只使用公开 Maven 依赖，不包含公司域名、内部包名、账号、环境枚举或私有平台协议。

## 文档入口

- [框架设计分析](ANALYSIS.md)：原 JAR 的架构、完整能力和设计取舍。
- [能力映射表](CAPABILITY-MAP.md)：原能力对应到通用类、SPI 或不迁移项。
- [学习实践路线](LEARNING-GUIDE.md)：按测试生命周期逐步熟悉框架。

## 快速上手（开箱即用）

环境要求：JDK 17+、Maven 3.9+。

```bash
# Linux / macOS / Git Bash
mvn test

# Windows CMD
mvn test
```

预期输出：`Tests run: 12, Failures: 0, Errors: 0`。

入门只需读一个文件：`src/test/java/io/testkit/basetest/demo/QuickStartDemoTest.java`。
它在一个类里演示了一条测试的完整生命周期：

```
YAML 数据驱动加载 -> @UseIdentity 身份租借 -> Mock 场景
    -> 业务调用 -> JsonDiff 严格断言（忽略动态字段）
    -> 监听器发布结果 -> 自动清理身份与上下文
```

配套数据文件在 `src/test/resources/io/testkit/basetest/demo/`：

| 文件 | 作用 |
|---|---|
| `order.yaml` | 用例套件：2 个启用用例 + 1 个 `enabled: false` 演示过滤 |
| `order-coupon-request.json5` | 请求载荷（JSON5 支持注释和尾逗号） |
| `order-coupon-response.json5` | 期望响应，`orderId` 为动态字段 |
| `order-fullprice-*.json5` | 第二条用例的请求与期望 |

建议阅读顺序：先跑通 `mvn test`，再按 [学习实践路线](LEARNING-GUIDE.md) 逐层深入。

### 目录结构

```
src/main/java/io/testkit/basetest/
├── BaseTest.java              # 测试基类：用例上下文绑定与清理
├── ReturnValueContext.java    # 线程隔离的跨步骤返回值存储
├── assertion/                 # JsonDiff 严格/宽松 JSON 比较
├── config/                    # YAML/JSON/JSON5 加载、环境配置合并
├── data/                      # 数据驱动：YAML 套件 -> 强类型方法参数
├── identity/                  # 线程隔离身份上下文 + 本地账号池
├── mock/                      # Mock 场景/规则/生命周期模型
├── runtime/                   # 运行时组合、TestNG 监听器、结果发布
├── coverage/  discovery/  job/  model/   # SPI 契约与通用模型
src/test/
├── java/.../demo/QuickStartDemoTest.java   # 开箱即用演示（从这里开始）
└── resources/.../demo/                     # 演示用 YAML 与 JSON5 数据
```

### 引入到自己的项目

```bash
mvn install   # 安装到本地仓库
```

```xml
<dependency>
    <groupId>io.testkit</groupId>
    <artifactId>base-test-toolkit</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

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

或在 Windows CMD 中：

```bat
build.bat
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
