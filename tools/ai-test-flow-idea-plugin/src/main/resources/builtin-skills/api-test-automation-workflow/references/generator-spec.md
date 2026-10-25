# 接口脚手架生成器规格（generation-spec v2）

## 入口与运行边界

接口脚手架生成器源码随 AI Test Flow 插件一起编译、打包和版本化。插件直接调用生成器 API，不依赖 `<skill-dir>/assets/api-test-scaffold-generator.jar`，成员机器也无需单独安装生成器。

在 IDEA 中先生成项目内的 `generation-spec.yaml`，再执行“生成接口自动化脚手架”。生成器只做确定性文件渲染和清单校验：

- 所有输入与输出都必须位于当前 Git 项目内。
- 默认拒绝覆盖任何已有目标文件。
- 不连接数据库、注册中心或真实接口。
- 不执行 Maven、Gradle、TestNG 或真实测试。
- 不修改构建文件，也不删除旧文件。

## Profile 与协议

框架 Profile 必须生成本项目框架的骨架，而不是独立通用 TestNG 脚本：测试类继承 BaseTest，通过 CaseContext、DataDriveUtil 读取用例 YAML 与请求/期望 JSON5，使用 JsonDiff 断言；API 调用独立封装，DataBuilder 保留业务造数与清理扩展点。packageName 与 resourcePackage 必须一致。API 和 DataBuilder 位于 src/main/java，测试类位于 src/test/java，用例与数据位于 src/test/resources 的对应包。插件不生成 POM、依赖、公共启动、环境配置和项目级运行入口；这些由外部初始化工程负责。本次接口的 testng.xml 只列举生成测试类。目录约定优先读取项目 .ai-test-flow/automation-project.yaml，模型不得覆写该约定。没有真实源码证据时不得虚构 BO/VO 实现或宣称造数已接通。

| `generator.profile` | 协议 | 用途 |
| --- | --- | --- |
| `basetest-http` | `HTTP` | 生成使用 `ApiClient` 的 HTTP TestNG 脚手架 |
| `basetest-dubbo` | `DUBBO` | 生成使用 `DubboClient` 的 Dubbo TestNG 脚手架 |
| `generic` | 独立生成器历史兼容 | 插件不接受，仅独立生成器支持 v1 的 `java.*` 自由模板 |

`basetest-http` 与 `basetest-dubbo` 必须使用 `schemaVersion: 2`。Profile 与 `target.protocol` 不匹配时，规格校验失败。

## 顶层字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `schemaVersion` | 是 | HTTP/Dubbo 新规格固定为 `2`；`1` 仅用于 generic 兼容 |
| `apiCode` | 是 | 稳定接口或场景编号，决定清单目录 |
| `sourceCaseIds` | 是 | 上游 TestCases YAML 的 `caseId` 集合，必须唯一 |
| `description` | 是 | 中文业务描述 |
| `packageName` | 是 | Java 测试包名 |
| `resourcePackage` | 否 | 资源包名，默认等于 `packageName` |
| `className` | 是 | TestNG 测试类名 |
| `author` | 否 | 默认 `AI` |
| `tester` | 视框架 | 用于 `${tester}` 模板替换 |
| `dataKey` | 是 | `DataDriveUtil` 加载键及 suite 文件名 |
| `template` | 是 | `query` 或 `data-preparation` |
| `environments` | 否 | 默认 `pre`、`test` |
| `target` | v2 是 | 目标协议 `HTTP` 或 `DUBBO`，可带目标模块 |
| `selector` | 建议 | 接口定位方式和值 |
| `endpoint` | v2 是 | HTTP 或 Dubbo 的已确认端点契约 |
| `types` | 否 | 已验证的请求、响应和 BO/VO/DTO 类型资产 |
| `evidence` | 否 | 支撑端点与类型结论的有序源码证据 |
| `generator` | 是 | Profile、输出根目录与协议配置 |
| `java` | generic 是 | 仅 `generic` Profile 使用的自由 Java 模板 |
| `cases` | 是 | 至少一个接口场景，每条映射上游 `caseId` |

`sourceCaseIds` 也可从每个 `cases[].sourceCaseId` 汇总，但建议在顶层明确声明。每个用例的 `sourceCaseId` 必须存在于顶层集合中。

## 接口选择器与发现证据

```yaml
target:
  protocol: HTTP
  module: order-service
selector:
  kind: HTTP_PATH
  value: POST /api/orders
```

选择器 `kind` 可表达 `API_CODE`、`HTTP_PATH`、`JAVA_SYMBOL` 或 `DUBBO_METHOD`。插件已预留接口发现 SPI，但当前没有真实 PSI/UAST、OpenAPI 或 Dubbo 元数据 Provider。`selector` 只是查询意图，不能单独证明调用链和类型；只有用户确认或仓库源码证据才能写入 `endpoint`、`types` 和 `evidence`。

```yaml
types:
  request:
    kind: BO
    fqcn: com.example.order.CreateOrderBO
    sourceFile: src/main/java/com/example/order/CreateOrderBO.java
    sha256: <source-sha256>
  response:
    kind: VO
    fqcn: com.example.order.OrderVO
    sourceFile: src/main/java/com/example/order/OrderVO.java
evidence:
  - order: 1
    kind: CONTROLLER
    symbol: com.example.order.OrderController#create
    file: src/main/java/com/example/order/OrderController.java
    line: 42
    sha256: <source-sha256>
```

生成器会把类型和证据元数据写入计划与清单，但不会据此复制或生成真实业务类。没有源码依据时必须留空或停止生成，禁止伪造 BO/VO/DTO。

## HTTP v2 规格

HTTP Profile 必须提供 `target.protocol: HTTP`、`generator.profile: basetest-http`，以及 HTTP 方法和以 `/` 开头的路径。

```yaml
schemaVersion: 2
apiCode: order_create_http
sourceCaseIds: [TC-HTTP-001]
description: 创建订单 HTTP 接口
packageName: com.example.generated.http
className: OrderHttpGeneratedTests
dataKey: order-http-cases
environments: [test]
target:
  protocol: HTTP
selector:
  kind: HTTP_PATH
  value: POST /api/orders
endpoint:
  method: POST
  path: /api/orders
  authenticated: true
  headers:
    X-Client: ai-test
generator:
  profile: basetest-http
  sourceRoot: src/test/java
  resourceRoot: src/test/resources
  metadataRoot: .api-test-generator
  baseUrlProperty: api.baseUrl
  defaultBaseUrl: http://localhost:8080/
  timeoutMillis: 10000
cases:
  - sourceCaseId: TC-HTTP-001
    name: 01_合法订单创建成功
    thought: 验证合法商品与数量能够创建订单
    requestRef: 01_合法订单创建成功.json
    expectRef: 01_合法订单创建成功.json
    request:
      base: {sku: SKU-1, quantity: 1}
    expect:
      base: {status: 200, body: {success: true}}
```

生成测试从系统属性 `baseUrlProperty` 读取基础地址，缺省使用 `defaultBaseUrl`。插件不会发出 HTTP 请求。

## Dubbo v2 规格

Dubbo Profile 必须提供 `target.protocol: DUBBO`、`generator.profile: basetest-dubbo`、接口类、方法和参数类型。多参数方法还必须提供等长的 `parameterNames`，用于从请求 JSON 对象取值。

```yaml
schemaVersion: 2
apiCode: order_create_dubbo
sourceCaseIds: [TC-DUBBO-001]
description: 创建订单 Dubbo 接口
packageName: com.example.generated.dubbo
className: OrderDubboGeneratedTests
dataKey: order-dubbo-cases
environments: [test]
target:
  protocol: DUBBO
selector:
  kind: JAVA_SYMBOL
  value: com.example.order.OrderService#create
endpoint:
  interfaceName: com.example.order.OrderService
  dubboMethod: create
  parameterTypes: [com.example.order.CreateOrderBO]
  parameterNames: [request]
  attachments:
    x-source: ai-test
generator:
  profile: basetest-dubbo
  sourceRoot: src/test/java
  resourceRoot: src/test/resources
  metadataRoot: .api-test-generator
  application: generated-api-tests
  registryProperty: dubbo.registry
  directUrlProperty: dubbo.directUrl
  group: ''
  version: ''
  timeoutMillis: 5000
cases:
  - sourceCaseId: TC-DUBBO-001
    name: 01_合法订单创建成功
    thought: 验证 Dubbo 泛化调用返回成功
    requestRef: 01_合法订单创建成功.json
    expectRef: 01_合法订单创建成功.json
    request:
      base: {sku: SKU-1, quantity: 1}
    expect:
      base: {success: true}
```

生成测试从系统属性读取注册中心或直连地址。插件不访问注册中心，也不执行泛化调用。

## generic 兼容 Java 字段

`java.baseClass`、`imports`、`classAnnotations`、`classMembers` 只用于 `generator.profile: generic`，且必须从目标仓库黄金测试提取。新建 HTTP/Dubbo 资产应使用 v2 Profile。

以下字段控制 DataProvider 和测试方法：

```yaml
java:
  baseClass: BaseTest
  imports: []
  classAnnotations:
    - '@TestOwner("automation")'
  classMembers: []
  dataProviderName: TestCase
  dataProviderMethod: prepareTestData
  dataLoaderExpression: 'DataDriveUtil.loadTestData(method, "${dataKey}", true)'
  testMethodName: test001
  testParameters:
    - FrameConfigInfo config
    - RequestRO request
    - JsonResponse<ResponseDTO> expect
  setupLines: []
  preparationLines: []
  actualType: JsonResponse<ResponseDTO>
  actualExpression: targetApi.query(request)
  diffExpression: DataDriveUtil.diffFieldValue(expect, actual)
  assertionLines: []
```

`data-preparation` 模板必须包含 `preparationLines`。`assertionLines` 为空时，生成器使用 `diffExpression` 和 `CollectionUtils.isEmpty` 形成默认字段断言；非空时完全采用指定断言。

## 用例与环境覆盖

每条 `cases` 包含：

```yaml
- sourceCaseId: API-001
  name: 01_主流程成功
  thought: 来源 STD-API-01，验证提交成功并返回 data=true
  mockId: null
  users: ["1000000"]
  requestRef: 01_主流程成功.json
  expectRef: 01_主流程成功.json
  request:
    base:
      id: __DATA_REF:created_record.id__
    overrides:
      pre: {}
      test: {}
  expect:
    base:
      data: true
    overrides: {}
  fieldHints:
    - field: id
      kind: runtime-business-data
      semantic: 本次运行创建的记录
      valueSource: record.create
      placeholder: __DATA_REF:created_record.id__
      required: true
```

`base` 生成全部环境共有内容；`overrides.<env>` 做递归覆盖。请求和期望会分别生成 `<name>_req_<env>.json5` 与 `<name>_exp_<env>.json5`。

`fieldHints.kind` 推荐取值：

- `fixed`
- `default`
- `business-enum`
- `runtime-business-data`
- `environment-base-data`
- `unique`
- `negative`

## 输出

```text
src/test/java/<package>/<ClassName>.java
src/test/resources/<resourcePackage>/<dataKey>.yaml
src/test/resources/<resourcePackage>/<case>_req_<env>.json5
src/test/resources/<resourcePackage>/<case>_exp_<env>.json5
.api-test-generator/<apiCode>/executableization-plan.yaml
.api-test-generator/<apiCode>/scaffold-manifest.yaml
```

清单记录源规格、所有脚手架文件和 SHA-256。可执行化计划提取每条用例的 `fieldHints`，并明确下一步交给 `make-api-tests-executable`。

HTTP/Dubbo v2 的清单和计划还记录 `sourceCaseIds`、协议、Profile、类型资产和发现证据。`sourceRoot`、`resourceRoot`、`metadataRoot` 可以调整，但解析后的路径必须留在项目根目录内。所有规格与产物都应提交 Git 并一起评审。

## 覆盖与 Modified 保护

1. 首次生成默认 `overwrite=false`；任一目标文件已存在时停止，避免覆盖用户工作。
2. 严格校验要求文件存在、条目合法且 SHA-256 与首次生成一致。
3. 二次可执行化或人工调整后，独立生成器的非严格校验可接受摘要变化并报告 `Modified`；`Missing` 和 `Malformed` 仍必须为 0。
4. IDEA 插件禁止覆盖已有产物；重新生成应选择新的输出目录或接口版本，并通过 Git 评审合并。
5. 生成器不删除清单之外的历史文件，也不自动回退工作树。

## 限制

- 生成器负责确定性渲染，不推断目标仓库 Java 类型和业务枚举。
- 接口发现 SPI 当前只是扩展边界，真实 PSI/UAST、OpenAPI 和 Dubbo Provider 尚未实现。
- 生成器不会连接数据库、服务注册中心或真实接口。
- 插件不会编译或执行真实测试；运行由开发者、自动化框架或 CI 完成。
- 二次加工必须由 Skill 结合项目证据完成。
- 生成器不删除旧文件，也不自动修改 Maven 配置。
