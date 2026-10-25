# Testkit自动化框架完整学习手册

版本基准：当前仓库1.1.0-SNAPSHOT；整理日期：2026-10-18。

这是一份合并后的单一学习文档：从工程接入、分层能力、执行流程、数据驱动和身份管理，到源码、断点、具体操作、造数清理、协议、日志、并发、企业扩展与排查，全部在本文。无需再打开之前分散的学习文档。源码链接是可选的代码定位入口，不是另一本必读手册。

学习目标：能独立使用框架、沿调用链调试、解释失败与资源归属、维护公共机制，并按明确契约接入企业平台。本文不把示例、TODO、noop或本地黄金集当成公司平台已经接通。

## 目录与阅读顺序

- [第一部分：框架全貌、能力分层与执行流程](#overview)
- [第二部分：环境准备、构建安装与工程接入](#setup)
- [第三部分：核心源码深度解析](#source)
- [第四部分：可对照使用的数据、身份与配置示例](#templates)
- [第五部分：其他模块、企业能力与维护](#modules)
- [第六部分：逐步上机实操——八个实验](#labs)
- [第七部分：业务修改、扩展练习与故障排查](#extension)
- [第八部分：学习验收、实验记录与能力边界](#finish)

第一次使用：先看第一、二部分，再直接跟第六部分实验1、3、4、5操作，遇到机制疑问回到第三部分。需要维护和扩展：完整学习第三、五、七部分。所有操作命令都明确工作目录；不需要同时启动框架、初始化器、插件三个服务。

先理解：框架是被业务测试依赖的JAR，不是要常驻启动的后端；本地订单service才是被测系统。纯框架单元实验不需要启动订单服务；真实HTTP/Dubbo实验需要。


<a id="overview"></a>

## 第一部分：框架全貌、能力分层与执行流程

### 先认识分层

```text
业务资产层        PRD / TAD / 功能用例 / 接口规格 / YAML / JSON5
测试编排层        TestNG测试类 / BaseTest / API封装 / 业务DataBuilder
运行时与生命周期  ProjectBootstrap / AutomationRuntime / Listener / Context
公共能力层        数据驱动 / 身份 / Mock / 断言 / 日志 / 报告 / 租约
协议与适配层      HTTP / Dubbo / SDK / JDBC / Kafka / Job
外部环境          被测服务 / 账号平台 / 造数平台 / 数据库 / 注册中心
```

这是一种职责划分，不表示每行都对应一个 Maven 模块。数据驱动、身份、HTTP 等多数在 base-test-toolkit；Dubbo/Kafka/XXL-JOB 等在 enterprise-adapters；公司业务实现放自动化项目或独立企业适配包。

为什么分层：需求变化不应该修改网络客户端；Token 平台变化不应该重写每个测试；增加日志不应该让每个用例复制 println；换 Dubbo 协议不应该重写 YAML 解析器。

### 模块总览与阅读入口

| 模块 | 核心入口 | 主要解决的问题 |
| --- | --- | --- |
| 基础生命周期 | BaseTest、runtime/PortableTestNgListener | 上下文绑定、每次调用的资源与结果收尾 |
| 数据驱动 | data/DataDriveUtil、CaseContext、TestCaseData | 数据和测试逻辑分离，参数绑定可校验 |
| 配置 | config/ConfigLoader、TestEnvironment、项目 ProjectSettings | 环境可切换、类型与来源可追踪 |
| 身份 | identity/IdentityProvider、IdentityContext、UseIdentity | 角色选择、账号租用、线程隔离、释放 |
| 造数 | data/DataFactory、DataPreparationRequest、业务 DataBuilder | 运行前依赖数据准备，不把固定文件当真实造数 |
| 返回值与清理 | ReturnValueContext、runtime/CleanupStack | 单次调用数据传递与失败后清理 |
| Mock | mock/MockEngine、MockSession、MockScenario | 可控依赖场景和生命周期 |
| HTTP | http/ApiClient、ApiResponse | 通信、认证入口、目标约束、诊断 |
| Dubbo | enterprise-adapters 中 dubbo/DubboClient | RPC 泛化调用、类型签名、引用生命周期 |
| SDK | sdk/SdkInvoker | SDK 调用包装与结果采集扩展 |
| 断言 | assertion/JsonDiff、JsonContract、Await | 结构化差异、契约、有限等待 |
| 执行装配 | runtime/AutomationRuntime、RuntimeRegistry、RuntimeProvider | 显式注入公共能力和调用级隔离 |
| 日志报告 | ProtocolCallLog、JsonLinesResultPublisher、TraceContext | 调用诊断、执行结论、关联追踪 |
| 企业组织 | enterprise 包、lease 包 | 归属、计划、分片、目标策略、共享资源租约 |
| 外部能力 | database、discovery、job，适配层 kafka/xxljob | 数据验证、发现、异步任务和消息接入 |

### 一条业务用例的真实执行链

以生成的 GoldenOrderHttpTest 为例：

1. Maven Surefire 或 IDEA 启动 TestNG。
2. 项目 ServiceLoader 注册的 ProjectBootstrap 在 execution start 加载环境默认配置，并安装 AutomationRuntime。
3. 测试类的 BeforeClass 创建业务 API 封装。此时配置应已就绪。
4. DataProvider 调用 DataDriveUtil，根据测试类包名和 suite 名找 YAML。
5. YAML 每条有效 case 被转换为 CaseContext、request、expected，一条 case 对应一次测试调用。
6. BaseTest 的 BeforeMethod 从参数中找到 CaseContext，绑定当前用例。
7. PortableTestNgListener 在测试方法执行前建立运行时/Trace 范围；若声明身份则获取身份；若有 mockId 则打开 MockSession。
8. 测试方法调用业务 DataBuilder.prepare，并登记 deferCleanup。
9. API 封装调用 ApiClient 或 DubboClient，记录请求和响应/异常。
10. 测试方法断言 HTTP 状态及业务响应，或断言 RPC 业务体。
11. Listener 收尾 Mock、身份和运行范围；BaseTest 的 AfterMethod 执行已登记清理并移除用例与返回值上下文。它们是各自负责的收尾边界，不应依赖猜测的全局顺序。
12. Listener 发布成功、失败、跳过或配置失败等结果，执行结束后形成汇总；项目 bootstrap 恢复此前运行时与其安装的默认属性。

具体 TestNG 回调存在测试方法与配置方法两种路径；不要把“继承 BaseTest”等同于自动安装全部企业能力。BaseTest 与 Listener 分工不同。

### 修改需求该去哪里

| 要修改的内容 | 应修改位置 | 不建议的位置 |
| --- | --- | --- |
| 接口路径或订单字段 | 业务API、规格、测试数据 | ApiClient 通用实现 |
| 增加边界场景 | 需求用例及运行时 YAML/JSON5 | 复制整个测试类 |
| 切环境地址 | 当前项目环境配置 | 每个测试写死URL |
| 接公司账号平台 | IdentityProvider实现 + bootstrap + 协议认证映射 | YAML写真实Token |
| 接公司造数平台 | DataFactory实现 + 业务DataBuilder | 修改JSON解析器调用平台 |
| 改全局调用日志 | 框架 ProtocolCallLog/协议接入 | 每个测试println |
| 加新中间件 | 独立适配器与契约测试 | 在BaseTest堆客户端 |
| 修改生成模板 | 初始化器或脚手架生成器，对应职责 | 手改框架业务包 |

### 推荐学习顺序与产出

| 阶段 | 主要源码 | 你必须产出的笔记或实验 |
| --- | --- | --- |
| 1 | 生成的测试类、ProjectBootstrap、BaseTest | 一条用例调用链与启动配置来源 |
| 2 | DataDriveUtil、ConfigLoader、StrictTypeModule | 参数绑定表、3种数据错误的断点记录 |
| 3 | IdentityProvider、IdentityContext、Listener | 身份选择优先级、租用与释放状态表 |
| 4 | RuntimeRegistry、RuntimeProvider、TraceContext | 全局与线程作用域的区别、并发实验 |
| 5 | CleanupStack、DataFactory、业务DataBuilder | 失败清理矩阵与部分成功补偿方案 |
| 6 | ApiClient、DubboClient、JsonDiff | 网络异常/业务响应/断言失败对照 |
| 7 | ProtocolCallLog、JsonLinesResultPublisher | 诊断日志与执行结果的关联与安全边界 |
| 8 | SdkInvoker、租约、企业执行 | 异步传播、取消、跨进程资源方案 |

可以每天一个阶段，也可以按熟悉程度拆分。完成标准不是“阅读完”，而是每阶段至少亲手制造一个失败并定位其发生层。

<a id="setup"></a>

## 第二部分：环境准备、构建安装与工程接入

### 环境准备

在 PowerShell 执行：

```powershell
cd E:\AITest\test
java -version
mvn -version
git --version
```

本套演练统一使用 JDK 21、Maven 3.9+。框架 Java 编译基线 17，初始化器本身可用 17，生成工程和插件开发使用 21。IDEA 的 Project SDK、Maven Runner JRE 也选 21；终端正常不代表 IDEA 配置相同。

首次构建需要下载依赖，联网和 Maven 私服设置应由本机环境准备；工程不包含公司账号与虚构私服地址。不要把 Token 写进 Git 配置文件。

### 工程一：自动化框架

#### 在 IDEA 中打开哪个目录

1. File → Open → 选择 `E:\AITest\test\baseTest\pom.xml`。
2. 按 Maven 工程导入，SDK 选 21。
3. 等待 Maven 同步，确认能看到 base-test-toolkit、enterprise-adapters、父 POM/BOM、示例等模块。
4. 学习时从 base-test-toolkit 进入，不要从所有历史 class 逐个阅读。

#### 先构建框架并安装到本机仓库

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/pom.xml -pl base-test-toolkit,enterprise-adapters -am install
```

成功标准：BUILD SUCCESS，并在本机 Maven 仓库安装当前 `1.1.0-SNAPSHOT`。自动化项目解析的是 Maven 依赖，不会因为你改了框架源码就自动使用新实现；修改后应重新 install，然后刷新业务工程 Maven。

本命令验证所选模块及上游，不等于执行了全部企业环境验收。若只为开发时快速编译，可以加 `-DskipTests`，但这不算测试通过证据。

需要框架整体生命周期、SBOM 和门禁验证时，使用框架 README 的完整入口；需 PowerShell 7.2+：

```powershell
cd E:\AITest\test\baseTest
pwsh -NoProfile -File .\verify-lifecycle.ps1
```

此完整入口的报告与所选模块 install 的范围不同，不要混报结果。任何会触达真实 Kafka、任务平台的 live profile，都要先确认独立测试环境及权限。

#### 框架怎么被业务项目引入

查看生成工程 POM：核心依赖是 `io.testkit:base-test-toolkit:1.1.0-SNAPSHOT`；Dubbo 工程还引入 enterprise-adapters 和所需协议依赖。需要 main 源码引用的类必须在编译类路径上，不能把所有框架依赖都改成 test scope。

职责归属：公共协议、数据解析、身份抽象、生命周期、报告放框架；订单路径、公司账号平台、造数规则等放业务项目或企业适配层。改业务路径不应重新发布公共框架。

#### 如何单独学习或验证一个模块

1. 在 IDEA 打开对应 `src/test/java` 的测试类。
2. 运行测试类，先读输入和断言，再读实现。
3. 数据驱动看 DataDriveUtilTest；身份看 IdentityContextTest；运行时看 RuntimeIsolationTest、LifecycleRegressionTest；日志看 ProtocolCallLogTest。
4. 用测试类定位实际能力，不要仅根据文档中的能力名称判断已接入公司平台。

对应源码机制在本文第三部分。

### 工程二：自动化项目初始化器

#### 从源码构建

```powershell
cd E:\AITest\test
mvn -B -ntp -f tools/automation-project-initializer/pom.xml clean package
```

成功后 JAR 位于 `tools/automation-project-initializer/target/automation-project-initializer.jar`。仓库现有分发包为 `dist/automation-project-initializer-1.0.1.jar`；重新构建后的最新结果应从 target 使用，不能默认旧 dist 包也自动更新了。

#### 创建 HTTP 自动化项目

下列路径仅为新工程示例；已存在时换一个新目录，不要强制覆盖：

```powershell
cd E:\AITest\test
java -jar tools/automation-project-initializer/target/automation-project-initializer.jar --type HTTP --directory E:/AITest/learning-http-tests --artifact-id learning-http-tests --group-id com.example --package com.example.automation.http --framework-version 1.1.0-SNAPSHOT
```

参数含义：type 决定模板；directory 决定磁盘位置；group-id/artifact-id 是 Maven 坐标；package 是基础 Java 包；framework-version 是需要解析的框架版本。

#### 创建 Dubbo 或 SDK 工程

沿用上面的命令，将 type 改为 DUBBO 或 SDK，同时修改 directory、artifact-id、package，避免混用同一目录。

Dubbo 模板包含 RPC 基础封装与依赖；SDK 模板只有适配扩展入口，还需自己提供真实 SDK 坐标、客户端、鉴权和释放实现。SDK 初始化成功不等于 SDK 方法用例已自动生成。

#### 打开并验证新工程

1. IDEA → File → Open，选择新工程的 pom.xml。
2. 导入 Maven，Project SDK 与 Maven Runner 选 21。
3. 查看 `src/main/java/<基础包>/bootstrap/ProjectBootstrap.java`。
4. 查看 `config/ProjectSettings.java` 和 `src/main/resources/environments/local.properties`。
5. 查看 `src/main/resources/META-INF/services/org.testng.ITestNGListener`，确认注册项目启动监听器。
6. 在新工程根目录执行 `mvn -B -ntp test`。
7. 预期初始化冒烟通过，并生成报告；此时没有真实业务用例，所以还不能判断 HTTP/Dubbo 联通。

#### 目录怎么使用

| 路径 | 放什么 | 日常由谁修改 |
| --- | --- | --- |
| pom.xml | 依赖、构建、测试插件 | 项目维护者；不由业务接口生成反复改写 |
| src/main/java/.../bootstrap | 项目能力装配和执行入口 | 自动化项目维护者 |
| src/main/java/.../config | 环境文件加载 | 自动化项目维护者 |
| src/main/java/.../api | 可复用协议/业务调用 | 开发者或插件 |
| src/main/java/.../data | 业务造数、清理扩展 | 开发者或插件 |
| src/main/resources/environments | local/test/pre 配置 | 项目维护者，不提交密钥 |
| src/test/java | TestNG 测试与断言编排 | 开发者或插件 |
| src/test/resources | YAML 套件和 JSON/JSON5 入参、期望 | 开发者或插件 |
| .ai-test-flow/automation-project.yaml | 工程类型与生成约定 | 初始化器生成，评审维护 |
| quality-assets | 需求、分析、功能用例、证据 | 使用插件并经人工评审 |

初始化器拒绝已有 Maven/Gradle 工程及文件冲突，这是保护机制。已有工程接入应按项目约定评审改造，而非删除旧 POM 再初始化。

<a id="source"></a>

## 第三部分：核心源码深度解析

### 数据驱动：逐方法读懂

源码入口：[DataDriveUtil.java](base-test-toolkit/src/main/java/io/testkit/basetest/data/DataDriveUtil.java)。

#### loadTestData(Method, String)

按实现拆成六步：

1. `method.getDeclaringClass().getPackageName()`：取方法声明类包名，而不是工作目录。
2. 点转斜线，再拼suiteName和.yaml，得到classpath路径。
3. 通过线程上下文ClassLoader查URL；找不到立即抛异常。
4. 打开输入流，在try-with-resources退出时关闭。
5. ConfigLoader.mapper读取TestDataDocument。
6. 传入以YAML URL为基准的payload解析函数，继续load。

为什么用URL而不直接转本机Path：classpath资源可能来自目录或JAR。另有显式Path重载，定位基础不同，但后续参数处理复用同一load逻辑。

需要警惕：suiteName默认值由测试类决定；换包、改suite名、大小写、资源复制配置都能导致加载失败。不是所有文件读取失败都属于模型或网络问题。

#### load：过滤和生成数据行

先检查根节点testDataSuite；遍历cases时，enabled为false跳过；debugCases非空时按case name筛选；每条保留case调用toArguments，最后返回Object[][]。

一行对应一次TestNG方法调用，不对应一个测试类。过滤到零行时应检查实际运行报告和选择条件，不能仅看BUILD SUCCESS就说业务都测了。

#### toArguments：最关键的绑定算法

假设签名：`verifyApi(CaseContext, Map<String,Object>, Map<String,Object>)`。

```text
payloads = request文件列表 + expect文件列表
arguments = 长度等于方法参数数量的数组
payloadIndex = 0
逐个参数检查：
  类型恰为CaseContext → 从case元数据构造，不消耗文件
  其他类型 → 消耗payloads[payloadIndex]，按该泛型类型解析，再递增
结束检查：文件是否全部且恰好消耗
```

CaseContext识别依靠类型，不是参数名context。request/expected识别依靠文件顺序，不是Java参数名。换参数顺序会改变绑定语义；文件个数相同也可能绑定到错误类型/业务含义。

反射得到的是`Type[]`，不是只拿`Class[]`，因此能保留Map/List的泛型信息。Jackson通过constructType构造JavaType，再readValue。思考：如果只用Map.class会丢失什么约束？如果DTO和Map都能解析同一JSON，为什么仍需要业务字段断言？

#### 配置解析与严格类型不是同一件事

ConfigLoader根据扩展名选择YAML或JSON mapper。当前.json5复用JSON mapper，只明确开启Java风格注释与尾逗号；不能仅凭扩展名推断支持JSON5完整语法，例如所有未加引号键、特殊数字等。

mapper关闭FAIL_ON_UNKNOWN_PROPERTIES，说明未知字段可能被忽略；这与StrictTypeModule“不允许某些标量隐式转换”并不矛盾。严格标量类型不等于完整Schema校验，更不等于业务必填/范围校验。

readPayload复制mapper后注册StrictTypeModule，避免为此路径直接改共享mapper配置。StrictTypeModule为String、整数、浮点、Boolean等注册反序列化器，检查JsonToken，再读取值。null、缺失字段、未覆盖的自定义类型仍需看对应反序列化行为和契约规则，不能一概声称全类型完全严格。

#### 数据不是越“自动补齐”越好

合法DTO方便强类型调用；Map便于保留非法输入。若测试目标是服务端拒绝字符串数量，而DTO在本地先拒绝字符串，测试根本没有触达服务。需要同时区分解析器单元测试与服务校验测试。

extraParams是扩展元数据；YAML中的environment不会自动替换ProjectSettings的test.env；sourceCaseId不会自动保证需求覆盖率。规则覆盖需要上游需求与生成清单、执行证据共同校验。

#### 本章实验与答案方向

| 实验 | 断点观察 | 正确归因 |
| --- | --- | --- |
| 改错suite名 | url=null | 类路径/命名，不是HTTP失败 |
| 少一个expect文件 | payloadIndex达到文件上限 | 参数绑定失败，未发业务请求 |
| 多一个payload | 最终消费数不等 | 数据契约错误 |
| DTO整数填字符串 | StrictTypeModule token不符 | 本地类型失败，不是服务校验通过 |
| Map整数填字符串 | request保留字符串 | 后续服务行为需要真实验证 |
| 加debugCases | rows数量减少 | 执行范围变化，不是覆盖率自动提高 |

对应测试：[DataDriveUtilTest.java](base-test-toolkit/src/test/java/io/testkit/basetest/data/DataDriveUtilTest.java)。先读其loadsYamlAndJson5Payloads方法，自己跟到Request/Response record被创建的位置。

### 身份管理：状态、所有权与失败路径

源码目录：[identity](base-test-toolkit/src/main/java/io/testkit/basetest/identity)。

#### 对象关系

```text
测试声明profile
  → IdentityProvider.acquire(profile)
  → UserIdentity（一次租用得到的身份）
  → IdentityContext.use(identity)（绑定当前线程）
  → 协议authenticated方法读取Token并传输
  → IdentityContext.close（恢复线程身份）
  → IdentityProvider.release（归还账号）
```

profile是角色池；alias是池内身份别名；subject是业务主体；accessToken是认证材料；attributes是附加信息。身份值对象本身不执行登录、刷新、锁定或注销。

#### InMemoryIdentityProvider的并发算法

构造时复制profile集合；acquire取候选列表的可变副本并shuffle，逐个按alias在ConcurrentHashMap执行putIfAbsent。首个成功插入表示租用成功；所有候选被占用或profile不存在则抛No identity is available。

随机顺序减少总选第一个账号的偏置，但不是严格公平调度，也不会等待空闲。锁的范围是同一provider实例；两个provider即使指向同一账号列表，也各有一份leased，因此可以重复租同一实际账号。

release按alias移除占用，不验证租约owner token。当前实现适合受控单JVM配对使用；如果旧租用方晚到的release可能与新租用交错，需要更强的租约世代/所有权设计，不能把这个内存实现当分布式租约服务。

#### IdentityContext的栈为何比单值ThreadLocal复杂

每个线程有Deque<IdentityContext>，use创建scope并push，current取peek；关闭时检查owner线程和栈顶是否自身，再pop，栈空remove。

它解决嵌套切换：buyer → reviewer → 关闭reviewer → 恢复buyer。单值ThreadLocal若只在finally remove，会丢掉外层buyer。closed标志让重复close幂等，但乱序close和跨线程close仍明确拒绝。

ThreadLocal.remove很重要：TestNG线程池会复用线程，不清理就会让后续场景读取前一身份。不要把ThreadLocal理解成“方法结束自动清空”。

#### Listener怎样决定用谁

`beforeInvocation`在测试方法分支中，依次检查方法UseIdentity、类UseIdentity，之后再检查YAML用户；注解存在就优先。YAML多个users且无注解明确报错，而不是自动多角色轮转。

状态变量的意义：state.identity表示已经拿到需归还的资源；state.scope表示已经建立需关闭的线程范围；二者单独保存，才能处理“拿到账号后建立上下文失败”等部分成功状态。

#### 获取失败与中途失败

| 故障位置 | 可能已获得资源 | 要做的事 |
| --- | --- | --- |
| acquire直接抛异常 | runtime/trace范围，未必有身份 | 标记失败并关闭已建立范围 |
| 身份绑定后Mock打开失败 | 身份、identity scope、runtime/trace | 继续归还身份，不因Mock失败跳过 |
| 业务断言失败 | 上述资源及业务前置数据 | Listener与BaseTest分别完成各自收尾 |
| release自身抛异常 | 身份归还未确认 | 记录失败，仍关闭其他范围；平台TTL补偿 |

不要用“finally都会清理”代替源码分析。必须找到资源在哪一行赋给state，以及对应cleanup是否检查null、是否继续执行其他动作。

#### 认证传输的两条实现

HTTP ApiClient.authenticated构造Authorization: Bearer，普通request不会自动加。DubboClient.authenticated接收tokenAttachment名字，从当前身份读取Token放入attachments，再invoke。

因此公司RPC附件键由你明确提供，不由框架猜。生成的本地订单API使用普通invoke/request，无鉴权服务的成功不能证明身份权限校验已验证。

#### 本章调试变量

- acquire：profile、candidates、candidate.alias、leased。
- use/close：STACK.get、owner、当前线程、栈顶、closed。
- Listener：annotation、context.users、profile、state.identity、state.scope。
- authenticated：Header/附件键是否正确，避免在截图中显示真实Token。

运行IdentityContextTest只能证明该测试写到的行为；当前其示例验证基本绑定、关闭和归还，不因方法名字含nested就假定所有乱序、跨线程、池争抢场景都有覆盖。自行补充这些实验的测试矩阵。

### 运行时：全局默认与调用级隔离

源码：[RuntimeRegistry.java](base-test-toolkit/src/main/java/io/testkit/basetest/runtime/RuntimeRegistry.java)。

#### current的选择顺序

`SCOPES`是ThreadLocal栈，`CURRENT`是AtomicReference全局默认。current先取当前线程栈顶，有则用该runtime；没有才读全局。

install只替换全局值，不覆盖已进入的线程scope。reset只重置全局默认，不会清掉每个工作线程已存在的scope。因此不能用reset作为“万能清理”。

AtomicReference保证单次引用读写可见性/原子性，不保证“保存旧值→install→执行→恢复旧值”这一组操作对并行类是事务。两类同时install/reset仍可能互相恢复错对象。

#### RuntimeProvider的价值

测试实例可实现RuntimeProvider返回自己的AutomationRuntime；Listener的runtimeFor优先复用已捕获State/配置runtime，其次实例provider，最后Registry.current。

每次调用创建State时保存runtime，后续发布和清理使用捕获值，避免执行中全局runtime变化导致账号从A池租用、却向B池归还或结果发到错误文件。

#### 默认runtime不是所有能力均可用

AutomationRuntime构造器为null能力补默认：身份获取抛unsupported；DataFactory/Job为unsupported；discovery空；Mock默认内存；results默认内存；coverage和invocationLogs可noop。

这种设计能让本地最小用例启动，但也意味着“未配置却不报错”不总是能力真实接入；未使用noop能力的测试可能成功。学习时分别问：实例存在吗、调用会失败吗、是否真的触达外部平台？

#### 可验证的不变量

1. 进入scope后install另一个全局值，当前线程current仍应是scope值。
2. 内层scope关闭恢复外层；全部关闭后回到当时的全局值。
3. 两个线程各自scope不串；没有scope的线程读取全局默认。
4. 不能跨线程或乱序close。

对应现有RuntimeIsolationTest及RuntimeIsolationFixture相关代码。其嵌套TestNG运行会产生自己的测试记录；阅读时区分外层回归断言与内层被验证用例数量。

### Listener：以资源状态机而非回调名理解

源码：[PortableTestNgListener.java](base-test-toolkit/src/main/java/io/testkit/basetest/runtime/PortableTestNgListener.java)。

#### 三组职责

- IExecutionListener：初始化本轮run标识、完成各publisher汇总。
- IInvokedMethodListener：配置/测试回调前建立范围、回调后清理。
- ITestListener/IConfigurationListener：结果与配置失败发布。
- IMethodInterceptor：企业执行计划筛选。

一个类实现多种Listener不代表任意回调顺序可随便假设；看其使用result attribute连接各回调的状态。

#### State有什么

runtime、runtimeScope、enterprise/快照、recordRunId、调用UUID caseId、trace、started、identity、scope、mock、cleaned。

资源变量分开且可为空，便于逐步构建、部分失败补偿；cleaned防止重复清理。State绑定具体ITestResult，而非保存在单个lastCase字段，因此数据驱动多次调用各有自己的state。

#### 两个beforeInvocation分支

配置方法分支仅建立该回调运行时与Trace并保存配置runtime；测试方法分支再做企业上下文、身份选择、Mock。

所以不要在BeforeClass里假定已经有某条数据行的buyer身份。BeforeClass属于类级配置，不天然属于任何一个case。需要身份的前置应选合适粒度并显式管理。

#### cleanup为什么用attempt

依次尝试Mock.close、IdentityContext.close、provider.release、EnterpriseContext.close、runtime scope.close、trace.close。每项异常都通过fail合并，继续下一项，而非第一项抛出就中止整个finally。

fail保留已有Throwable，把额外失败加suppressed并设ITestResult为FAILURE。因此原始断言通常可以保留，同时看见清理失败。调试时展开suppressed，不只看最外层错误文字。

#### finish怎样避免重复记录

对ITestResult同步检查FINISHED标志，第一次设置后处理，后续返回。状态映射SUCCESS/PASSED、SKIP/SKIPPED、其他FAILED；phase区分configuration与test。

它维护publisher到runId的映射，拒绝同一个publisher混用不同execution run IDs；按publisher累计通过/失败/跳过，onExecutionFinish再complete。

发布失败会标记测试失败，并以失败计数进入汇总，但如果publish根本没有写成功，不能从“程序试图发布”推断文件一定有对应行。完整性门禁需要校验报告和汇总，而不是只信内存计数。

#### 本章必做回归阅读

阅读LifecycleRegressionTest的modes，再读LifecycleFixture具体场景，跟踪acquired/released/bodies/records/summaries。外层断言`released==acquired`就是资源守恒检查；不能只核对最终hasFailure。

自己补一张表：模式、故障点、测试体是否执行、身份是否释放、原始异常是否保留、结果条数、summary计数。答案必须从fixture读取，不要根据模式编号猜。

### BaseTest与业务清理：两个资源边界

源码：[BaseTest.java](base-test-toolkit/src/main/java/io/testkit/basetest/BaseTest.java)、[CleanupStack.java](base-test-toolkit/src/main/java/io/testkit/basetest/runtime/CleanupStack.java)。

#### BaseTest只做少量通用工作

BeforeMethod从参数查CaseContext；currentCase没有绑定则抛异常。deferCleanup把动作加入当前测试实例的线程局部CleanupStack。AfterMethod先close栈，finally remove栈、CaseContextHolder、ReturnValueContext。

Listener身份资源与BaseTest业务资源分属两个边界。不要未经验证假设业务cleanup时仍存在Listener为测试方法建立的身份/Trace；需要身份的清理应显式捕获合适数据、使用自有受控凭证或设计独立作用域。这里尤其值得用断点验证具体回调顺序。

#### CleanupStack的LIFO与异常聚合

add用push；close用pop，因此后注册先关闭。创建订单前先建库存，则先清订单再清库存符合依赖逆序。closed先置true，所有动作依次尝试；捕获Exception或Error并加入聚合异常，最后有suppressed才抛出。

重复close直接返回，关闭后add拒绝。它没有线程owner检查，本身也不是并发集合；BaseTest通过ThreadLocal使用降低共享风险。若你手工把同一个CleanupStack跨线程共享，不能假定自动安全。

#### 业务DataBuilder与DataFactory

当前生成prepare复制Map、cleanup留扩展；DataFactory默认unsupported。框架并没有因为存在这些类就完成真实造数平台接入。

正确顺序：申请资源 → 立即登记补偿 → 再申请下一资源 → 调用/断言 → 逆序补偿。若prepare一次性创建多种资源后才返回，外层deferCleanup只能覆盖返回后的失败，prepare内部要有部分成功补偿。

ReturnValueContext是线程局部Map，只用于当前调用中的命名数据传递；不自动跨线程传播，也不适合作为跨用例依赖仓库。

#### 编译期DataBuilder注解

框架@DataBuilder和DataBuilderProcessor会为符合要求的方法生成`_DataBuilderAdapter`。这是编译期适配，不是运行时扫描后直接改原类；并且与插件命名为XxxDataBuilder的普通业务类不同。

学习时检查processor注册/编译器annotation processing设置、生成源目录、方法访问限制与输出TestNG注解。仅在类名里含DataBuilder不会触发编译处理器。

### 协议源码：网络成功与测试成功分开

#### HTTP ApiClient

构造器解析并校验base、超时，建立不跟随重定向的JDK HttpClient。request先校验相对路径及目标同主机同端口，企业模式再检查目标策略；然后记录诊断REQUEST，编码JSON并send，返回ApiResponse。

HTTP4xx/5xx仍是响应对象，是否符合预期交给测试。InterruptedException恢复中断标志后转换异常；IO失败与RuntimeException会在诊断中记录ERROR。finally负责诊断完成及既有调用指标路径，不能把诊断失败语义与自定义sink失败语义混为一谈。

业务API封装指定POST /demo/orders及请求结构；ApiClient不应该内置订单规则。如果要重试下单，必须考虑幂等键和副作用，不能仅为偶发超时给所有调用加通用重试。

#### DubboClient

构造ReferenceConfig：generic=true、retries=0、timeout、group/version、注册中心/直连；首次invoke才reference.get。invoke与close是synchronized，同一个客户端实例上的调用会被串行化，不能仅打开TestNG多线程就假设同一实例产生并发RPC。

invoke先校验类型数量与参数数量，保存当前RpcContext附件快照，再附加本次附件并$invoke；finally清空本次附件并恢复原附件，避免线程复用泄漏。response无HTTP状态码。

authenticated只是把当前Token映射到指定attachment键。close设置closed并destroy引用，后续invoke拒绝。读代码时分别关注“RPC引用”与“全局Dubbo运行框架”，关闭引用不等于停止服务端。

#### 本章断点与实验

HTTP观察base.resolve后的target，尤其base有前缀而path以/开头的情况。Dubbo观察parameterTypes/arguments、附件调用前后状态、reference.get第一次连接。

实验包括：不存在端口、错误RPC方法签名、400业务负例、超时、关闭客户端后再次调用。先预测失败层，再运行验证；不能把预期异常全部catch后当通过，需断言具体类型/行为。

### 断言源码：递归、类型与可观测差异

JsonDiff把对象转JsonNode，从$路径递归比较。检查忽略路径、缺失、节点类型，再处理对象字段、数组或标量；strict会检查额外实际字段，lenient仅放宽相应额外字段约束，不代表任意类型和值都忽略。

返回List<Difference>，不会直接调用TestNG.fail。业务测试必须对差异列表断言。动态字段忽略应最小化，忽略整个body虽能变绿，却失去测试意义。

Await.until使用nanoTime计时，重复调用supplier并检查predicate，睡眠不超过剩余等待预算；supplier异常直接传播。重要边界：supplier若自身阻塞很久，Await不能强行在总预算到点时打断它，因此查询本身也应有超时。不要把总等待配置当外部API硬截止时间。

练习：修改返回JSON的类型、缺字段、多字段、数组元素，分别记录Difference.path/type。再写一个supplier抛异常，确认不是等待到超时才失败。

### 日志与报告：读懂两个不同失败策略

#### ProtocolCallLog

start建立callId，捕获当前Trace、CaseContext、线程和环境，发送REQUEST；Call保存options和起始nanoTime，complete产生RESPONSE或ERROR，并用finished避免重复完成。

字段先脱敏再输出，目标URL有安全处理；正文开关、截断和文件轮转都是独立策略。默认摘要开启，local/test模板显式开启正文，pre关闭正文。日志IO/序列化失败安全告警，不应篡改网络结果。

断点：start、Call.complete、脱敏函数、文件输出分支。观察原始请求与处理后Map，确认测试数据有敏感字段时输出被屏蔽，不能为了观察把真实密钥贴到笔记里。

#### JsonLinesResultPublisher

publish写type=test；accept写type=invocation；complete写type=summary。用白名单输出结构化指标，不原样写任意metadata和Throwable消息；详细断言仍看TestNG报告，不能假定所有报告都具有相同脱敏保障。

append在当前实例上synchronized，使用UTF8 CREATE+APPEND，不保持长期开启writer。这个锁只保护同一个publisher实例，不保护其他实例或其他JVM写同一文件；每进程/worker独立文件再汇总更清楚。

结果文件写入失败会抛异常，这与“诊断日志失败不改变传输结果”不同：结果丢失是执行完整性问题。onExecutionFinish汇总失败同样需要暴露。

#### 三种ID与两种成功

runId关联一轮执行；调用级caseId关联具体用例实例；callId关联一次协议请求。逻辑caseId/sourceCaseId用于稳定业务追溯，不能与执行UUID混用。

响应成功与断言成功是两种事；日志落盘成功与结果完整也是两种事。学会交叉核对，而非只搜ERROR关键字。

### SDK异步与线程隔离的真实边界

源码：[SdkInvoker.java](base-test-toolkit/src/main/java/io/testkit/basetest/sdk/SdkInvoker.java)。

构造固定worker数、有界ArrayBlockingQueue、AbortPolicy。提交前捕获runtime、identity、trace、enterprise快照；worker里用try-with-resources逐一恢复作用域，然后执行Callable。它没有自动传播任意ThreadLocal，例如不要假定CaseContextHolder和ReturnValueContext也随之传播。

Future.get限时等待；超时/中断cancel(true)，中断路径恢复调用线程中断标志；finally从pending和队列移除，并向捕获runtime的invocationLogs发指标。没有隐式重试。

cancel(true)是中断请求，不保证SDK已停止远程操作。action若忽略中断，调用线程可以已报告超时而worker仍在执行；此时归还账号或清理资源可能和仍在进行的工作冲突，真实SDK接入要另行设计隔离、客户端超时、幂等和完成确认。

close对pending取消并shutdownNow，不等于所有任务已完成终止。线程为daemon也不能代替资源生命周期治理。

本章验证：队列满、关闭后提交、超时、忽略中断、嵌套身份作用域、结果sink抛异常。对应SdkShutdownTest、SdkFailureReportingTest。不要把同一测试通过外推成所有第三方SDK的取消行为都正确。

### 深入其他模块时怎么读

#### Mock

InMemoryMockEngine维护sessionId到scenario的并发Map，close移除记录。它明确不拦截网络流量。YAML有mockId但没有真实代理/规则接入，不能声称服务外部依赖已被替换。

读真实HTTP Mock实现时，应查匹配、端口占用、请求并发、关闭行为和隔离策略。Mock准确性要以出站调用证据验证，而非只看activeScenarios非空。

#### 数据库租约

JdbcResourceLeaseProvider使用数据库时间、owner token、fence与条件更新，非内存账号池的同一算法。查看tryAcquire、续租、close的WHERE条件，确认旧owner不能错误释放新owner。

当前注释明确TTL为1秒—1小时，语句超时5秒，无后台自动续租；连接获取超时由连接池负责；H2覆盖不等于真实数据库方言/多CI节点都已验收。fence要对下游产生实际约束才具备相应防旧写效果，不能只生成一个数就宣称所有资源已防脑裂。

#### 企业计划与分片

按CaseDefinition、EnterprisePlan、上下文和目标策略读：逻辑用例身份如何定义、如何选择/分片、owner/team/service如何入报告、越权目标如何拒绝。再看企业示例的计划与结果门禁。

稳定分片解决执行分工，不解决共享账号和数据库冲突；团队归属元数据不等于企业访问控制平台。每项治理能力都要对照实际执行约束和拒绝路径测试。

### 扩展案例：设计一个真实身份Provider

这里是需自行实现的工程设计练习，不是已经连接公司账号平台的成品。不要在未获授权环境调用真实账号接口。

#### 先定义平台契约

租用输入：environment、profile、runId、请求幂等键；租用输出：leaseId、ownerToken、alias、subject、accessToken、expiresAt。归还输入需带leaseId和ownerToken，不只alias。租用/登录/刷新超时、重试范围、失败码需明确。

框架IdentityProvider返回UserIdentity而不直接暴露lease对象，你可以在适配器内部维护身份到租约记录的受控映射，但要明确同alias重租的世代、并发、过期回收和日志脱敏；不能用一个永远增长的static Map草草解决。

#### 装配位置

1. 在自动化项目main的适配包实现IdentityProvider，平台客户端单独封装。
2. ProjectBootstrap创建provider并装入AutomationRuntime。
3. 测试声明UseIdentity或YAML单users。
4. HTTP用authenticated，Dubbo用指定tokenAttachment的authenticated，或经过评审的业务协议封装。
5. 执行结束关闭平台客户端；单次调用release；进程崩溃由平台TTL兜底。

#### 测试矩阵

| 场景 | 必须验证的结果 |
| --- | --- |
| 正常租用/归还 | 一次租用有对应归还，Token不落日志 |
| 池耗尽 | 明确失败，不返回假账号 |
| 登录失败 | 已租资源得到补偿，不继续业务调用 |
| 业务断言失败 | 仍归还，不覆盖原始断言 |
| 归还超时 | 暴露失败/待补偿状态，TTL兜底 |
| 旧租约晚归还 | 不释放新租约 |
| 并行/多JVM | 平台原子租用，不靠本机ThreadLocal |
| Token过期 | 刷新/重取按明确策略，避免无脑重试副作用 |

替代方案是每次新建独立测试用户，隔离强但成本高；共享静态Token成本低却无法代表权限覆盖与并发租用。选型必须说明账号容量、执行时间、稳定性和安全代价。

### 扩展案例：造数平台适配与补偿

1. 选择一个最小场景，如创建独立测试库存，不要一开始自动初始化整套环境。
2. 定义DataPreparationRequest的name及inputs结构、必填字段、环境与身份来源。
3. 实现DataFactory，返回明确资源ID、所有权和必要清理标识。
4. 在业务DataBuilder中仅填动态前置字段，不能覆盖显式负例。
5. prepare内部记录每项已创建资源，部分失败时逆序补偿；成功后将清理责任交给调用方。
6. 测试方法收到成功结果后立即deferCleanup；清理用幂等删除/回收，不删其他运行的数据。
7. 凭证不要只依赖测试方法已关闭的身份scope；明确清理权限与生命周期。
8. 进程崩溃或超时仍在执行的情况需租约/TTL/补偿任务，而不是只写finally。

验收：成功、准备一半失败、断言失败、清理失败、重复清理、并发、超时后远端实际成功、服务重启。每项检查资源归属，不能只核对测试退出码。

AI可协助编排输入，但不能凭推测创建ID或把平台错误转成“已准备”。真实造数成功需要平台结果及可验证业务状态。

### 单元测试与回归：怎样形成源码证据

快速针对性测试可在框架模块执行：

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=DataDriveUtilTest,IdentityContextTest,RuntimeIsolationTest,LifecycleRegressionTest,ProtocolCallLogTest,SdkShutdownTest,SdkFailureReportingTest" test
```

这只是选中测试，不是全框架、真实平台或完整HTTP/Dubbo验收。报告位于模块target/surefire-reports。日志/格式检查与完整生命周期门禁按框架README另行执行。

本资料整理时已实际执行上述命令：2026-10-18，19条通过，0失败、0错误、0跳过。测试包含诊断输出失败的保护场景，控制台可能出现诊断写入不可用告警；判断该场景应结合测试断言与最终报告，而不是单看告警就认定业务调用失败。这不是任意写盘告警都可忽略的授权，真实运行仍要检查日志路径与权限。

阅读回归测试时按三层做笔记：输入故障、观察量、断言。观察量包括调用次数、身份租用/释放数、scope是否恢复、资源剩余数、结果行数、summary计数、异常suppressed。

只断言“发生异常”太宽；要确定异常在正确层产生，且不留下资源、不中断后续独立清理、不把原始失败覆盖。对于超时和并发尽量用有界等待、栅栏/事件等机制控制，而非随机sleep碰运气。

### 故障推演：先写预测，再调试

| 问题 | 你应追到的实现 |
| --- | --- |
| 换包后YAML找不到 | method声明类包路径与classpath资源定位 |
| 有buyer配置但Header没Token | provider/context与authenticated的边界 |
| 并行类结果写错文件 | 全局install与实例RuntimeProvider、捕获runtime |
| Mock关闭抛错后账号未归还？ | cleanup.attempt是否继续、suppressed |
| 归还后下条用例还读到身份 | IdentityContext作用域是否关闭、线程复用 |
| 业务断言成功但构建失败 | AfterMethod清理、sink发布、汇总、格式门禁 |
| 网络日志成功但报告缺行 | 诊断日志与结果publisher是不同路径 |
| SDK已超时但远端仍创建成功 | cancel(true)非强制终止、远端幂等/补偿 |
| Await总超时却等了更久 | supplier自身阻塞未被Await打断 |
| ThreadLocal全部用了仍抢账号 | 锁范围是线程/JVM/数据库哪一层 |

写下每个预测的“应该修改哪层”。不允许一遇到错误就加重试、忽略断言、关门禁或扩大超时。

### 深度学习验收与复述模板

你需要能不看文档完成以下任务：

1. 从一个YAML行跟到方法参数，再跟到实际HTTP/RPC请求。
2. 解释注解、YAML用户、provider、context和认证函数的各自职责。
3. 证明两个并行实例的runtime和结果不串，不仅口头说ThreadLocal。
4. 制造原始断言+清理失败，指出主异常与suppressed分别在哪。
5. 解释当前SDK传播了哪些上下文、没传播哪些，以及超时后什么不能保证。
6. 说清内存账号池与数据库租约的所有权、范围和失效恢复差别。
7. 设计并验证一个真实平台适配器，不改所有业务测试方法。
8. 识别文档/示例与实现边界，不把TODO、noop和本地黄金集当企业接入完成。

复述一个模块使用以下顺序：具体失败是什么 → 调用入口 → 内部数据结构/算法 → 成功与失败路径 → 资源所有权 → 测试证据 → 替代方案与代价 → 当前未覆盖边界。

建议保留个人学习记录：源码版本、断点截图（脱敏）、调用链、失败实验、修复前后结果、未理解问题。真正深入的标准是能预测行为并用实验验证，而不是能背出所有类名。


<a id="templates"></a>

## 第四部分：可对照使用的数据、身份与配置示例

### 最小运行时 YAML

```yaml
testDataSuite:
  description: 创建订单
  cases:
    - name: 正常下单
      thought: 数量为1时可成功
      enabled: true
      users: []
      extraParams:
        sourceCaseId: ORDER-001
      request:
        - order_req.json5
      expect:
        - order_exp.json5
```

同目录 `order_req.json5`：

```json
{"sku":"SKU-DEMO","quantity":1}
```

`order_exp.json5`：

```json
{"status":201,"body":{"code":"OK","orderId":"ORDER-DEMO-001"}}
```

此 expected 的 status/body 外壳由当前 HTTP 测试方法解释；JsonDiff 本身不知道 HTTP 状态码。Dubbo 期望直接按实际测试类中的业务体结构维护，不要机械复制 HTTP status。

### 本地账号池接入示例

以下是需要你在测试工程增加的示例，不是当前无鉴权订单服务的现有配置：

```java
var buyer = new UserIdentity("buyer-local-1", "user-1001", "local-demo-token", Map.of());
var identities = new InMemoryIdentityProvider(Map.of("buyer", List.of(buyer)));
var report = new JsonLinesResultPublisher(Path.of("target/identity-demo.jsonl"));
var runtime = new AutomationRuntime(identities, null, report, null,
    report, null, null, null);
```

在项目 bootstrap 的既有 runtime 装配处替换 identities；不要为每个测试方法安装一份全局 runtime。上面未列 imports，按实际类导入；示例 Token 仅为本地演示，不应替换为 Git 中的真实凭证。

测试类或方法添加：

```java
@UseIdentity("buyer")
```

也可在单个 YAML case 设置 `users: [buyer]`。二者同时存在时以注解为准，不要期望 YAML 覆盖方法注解。

### 手工作用域及释放

```java
UserIdentity identity = identities.acquire("buyer");
try (IdentityContext scope = IdentityContext.use(identity)) {
    UserIdentity active = IdentityContext.requireCurrent();
    // 此处调用显式使用当前身份的业务API。
} finally {
    identities.release(identity);
}
```

IdentityContext.close 只恢复当前线程上下文，不自动调用 provider.release；Listener 自动流程会分别完成两件事。手工流程忘记 release 会耗尽池；只 release 不 close 会在线程池复用时串身份。

作用域必须在创建线程按后进先出关闭；内层 buyer 切 reviewer 后，内层关闭应恢复 buyer。不能把 scope 传到另一个线程 close。

### 配置与环境管理

框架的 ConfigLoader/TestEnvironment 是通用配置能力；初始化器生成的 ProjectSettings 是项目的环境文件加载器，二者不是同一个类。

当前 ProjectSettings 先取系统属性 test.env，再取 TEST_ENV，默认 local；加载 classpath 下 environments/<env>.properties，仅为尚未设置的系统属性填默认值。已有 -D 属性优先。执行结束只清理由它安装且值未改变的默认属性。

因此：配置写在 src/main/resources，不要只改 target/classes；切环境会读另一份文件；IDEA 的旧 VM options 可能覆盖文件；环境配置不在类路径会启动失败。Properties 加载使用 Java Properties 的语义，含中文/特殊转义应按实际编码行为测试，不把敏感值直接写文件。

为何不写死地址：同一测试代码要可复用于 local/test/pre。为何仍保留 -D：CI 临时覆盖便利。代价是来源优先级必须清楚，排查时要核对实际执行环境和 VM options。

<a id="modules"></a>

## 第五部分：其他模块、企业能力与维护

### 配置模块补全：不要混用两套加载机制

初始化器生成的ProjectSettings读取environments/*.properties，配置装入尚未设置的系统属性；框架EnvironmentConfig读取显式指定的YAML，返回不可变values映射。两者不是自动互相转换。TestEnvironment主要表达当前环境及属性，设置test.env也不会自动改写已有客户端地址。

EnvironmentConfig的源码顺序：读YAML → 检查选中profile存在 → flatten common → flatten选中环境 → 覆盖TEST_ATTR_* → 覆盖test.attr.* → 按capabilities筛选 → 解析${环境变量} → 返回对象。覆盖顺序为common < 选中环境 < 环境覆盖 < JVM覆盖；capabilities非空时只解析所需能力，纯HTTP项目无需为没使用的Kafka配置秘密。

ConfigCatalog按kind:name合并ConnectionSpec，localFirst决定覆盖方向，不访问公司配置中心。TestClients根据EnvironmentConfig按需创建HTTP/SDK客户端；不是配置里写了Dubbo就自动建立所有外部连接。

操作：打开config/EnvironmentConfig.java，在flatten后、values.replaceAll前后设置断点；IDEA运行EnterpriseCapabilitiesTest、TestClientsTest、ConfigCatalogTest中的相关方法，观察values、profile、capabilities和覆盖来源。先读测试再决定改哪个输入，未知profile和缺变量应明确失败，而非回退到生产默认值。

### JDBC：事务回滚与业务清理的区别

JdbcSession构造时持有独占Connection并设置autoCommit=false；prepare创建PreparedStatement，设置查询超时和最大行数，用setObject绑定参数；query用列标签组装Map，update返回影响行数；commit显式提交；close先rollback未提交修改再关闭连接，异常合并。

操作：打开database/JdbcSessionTest.java及JdbcSession.java，在构造器、prepare、commit、close打断点，Debug现有测试。检查autoCommit、参数从索引1开始、查询返回值和close后的事务效果。测试使用H2，不需要连接公司数据库。

最重要的边界：未提交造数可能对另一个业务JVM不可见；已经commit的数据不会因close时rollback被删除。为了让服务看到数据而commit之后，必须登记显式业务补偿。PreparedStatement保护参数值，不表示动态表名和SQL字符串随便拼接也安全。JDBC查询超时与连接池获取超时分属不同层。

### 真实HTTP Mock：如何让被测系统真的走桩

InMemoryMockEngine只登记场景；HttpMockEngine为每个Session创建127.0.0.1随机端口的HTTP桩，按method/path及可选JSON body匹配规则，跟踪调用次数。close会验证相应行为并释放服务资源，具体失败以测试断言为准。

操作：打开mock/HttpMockEngineTest.java，在engine.open、Session.baseUrl、请求匹配和close设置断点，Debug测试。观察规则catalog、随机端口、命中计数；再读HttpMockEngine.open确认无规则会失败。

接真实业务时，先把被测服务的下游地址显式改到Session.baseUrl，再触发业务并验证命中。只在测试JVM开了桩，却让远端服务仍访问原地址，不会自动拦截远端流量。ThreadLocal中的currentBaseUrl也不能自动跨JVM传递。

### 任务：触发确认、执行终态、业务结果三层验证

JobService定义trigger/query；LocalJobService做本地调度；HttpJobService对接显式测试网关；XxlJobService适配特定管理端契约。触发成功不等于任务执行成功，任务执行成功也不等于最终订单/数据库状态正确。

JobAwait.succeeded重复查询已知execution id：未找到继续等待，FAILED/CANCELLED立即失败，UNKNOWN立即要求先核对原执行；不是遇到未知结果就重新提交任务。查询返回别的id也失败。异步等待依赖查询本身的超时。

操作：依次Debug job/LocalJobServiceTest、JobAwaitTest、HttpJobServiceTest；在JobAwait谓词中观察execution.id/state。再阅读enterprise-adapters的xxljob/XxlJobServiceTest，明确它验证本地HTTP契约，不代表实际公司调度平台联通。

真实接入前确认环境、白名单任务、登录方式、日志关联和触发权限；不要为了学习随意触发公司任务。失败重试要依据幂等和结果核对，不能盲目重提。

### Kafka与消息：观察器和消费业务不是一回事

KafkaPublisher负责发送及确认；KafkaMessageProbe使用独立测试group观察消息，禁止自动提交、保存poll批内尚未读取记录并按关联条件过滤；KafkaConsumerRunner负责消费处理和相应offset行为。MessageProbe是证据获取能力，不表示被测业务消费者已正确落库。

操作：打开enterprise-adapters/src/test/java/io/testkit/adapters/kafka/KafkaAdaptersTest.java，Debug其中使用MockProducer/MockConsumer的测试；对应源码断点放在发送确认、ready、poll/缓冲、匹配和关闭处。观察topic、group、key、headers、runId及offset，不能只检查value中出现一个字符串。

真实场景顺序：建立独立观察器并确认ready → 触发业务/发送 → 按本轮业务ID读取目标消息 → 等待最终业务状态 → 断言 → 关闭与清理。观察器若晚启动，可能漏掉瞬时证据。不能使用生产消费group做学习实验。

MockConsumer/MockProducer不验证真实broker鉴权、rebalance、网络中断和重复投递。KafkaLiveIT需专用平台和授权配置，本手册不自动开启live profile，也不把本地测试算成真实Kafka验收。

### 服务发现：运行时路由，不是插件源码扫描

StaticServiceDiscovery复制实例列表，discover先过滤healthy，再按metadata中的service/environment/lane/zone匹配；查询字段null/空白为不限。返回静态结果不会自动注册心跳或接入公司注册中心。

操作：打开discovery/StaticServiceDiscoveryTest.java和实现类，在stream过滤处打断点，观察ServiceQuery和实例metadata；核对健康状态与环境错误为何被过滤。插件用于提取接口BO/VO的发现SPI是另一种能力，不要混用。

### 结构化契约：JsonContract不等于完整JSON Schema

JsonContract.of接收JsonNode，required用JSON Pointer检查存在和类型，nonBlank检查非空字符串，arraySize验证数组长度。JSON Pointer如/code与JsonDiff的$.code路径语法不同。

操作：Debug assertion/JsonContractTest.java，断点放在at与required的类型分支；用现有错误输入观察AssertionError。契约检查用于结构，业务金额与权限依然需要业务断言。不要把不输出响应值的契约错误外推到所有TestNG报告都已脱敏。

### 企业执行：计划准入、分片与证据完整性

EnterpriseRuntimeProvider提供运行与策略；TestCaseMetadata提供稳定ID、归属等；EnterprisePlan在方法级选择前验证元数据与策略，再按级别和稳定分片筛选并保存计划。DataProvider展开在方法选择之后，所以不是按每个YAML行自动均分到所有worker。

跨分片测试须独立执行；当前计划会拒绝不支持的TestNG方法/组依赖。严格企业模式缺provider、无效级别、重复逻辑ID或零选择等应失败，不能静默变绿。TargetPolicy约束执行目标，不等同整个公司的IAM权限系统。

操作：运行enterprise/EnterpriseModeTest、EnterprisePlanManifestTest、TargetPolicyTest、TestCaseMetadataTest，Debug EnterprisePlan.select，观察definition、planned、assigned和manifest。并行资源锁另看lease/JdbcResourceLeaseTest；分片不负责账号互斥。

团队示例可在安装框架后执行：

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/enterprise-team-example/pom.xml test
```

执行前阅读本工程POM与配置，保持本地模式，不擅自切到远端环境。检查测试报告、计划与JSONL；不要仅看进程成功。100人协作还依赖制品版本、环境隔离、账号容量、造数容量、密钥与评审制度。

### 覆盖率、远程结果平台与HTML报告：扩展边界

CoverageCollector/Request/Artifact是采集契约，默认可noop；有接口不代表已自动挂载JaCoCo或采集被测远端JVM。远程ResultPublisher需自己实现平台鉴权、超时、幂等和故障策略；JSONL结果也不等于已接入Allure/企业HTML平台。

学习时先定义产物的真实性：采集哪个JVM、哪一版代码、对应哪次run、失败是否阻断以及如何核验证据。可以在批准环境实现适配器，不能用虚构覆盖率数值让报告显得完整。

### 构建、发布、CI与日常维护

只改业务路径/用例数据不要发布框架；改公共能力后运行模块测试并install，让业务项目刷新依赖。正式release通过父POM/BOM批准版本发布，不能长期依赖不同机器内容不一致的SNAPSHOT。

完整框架生命周期入口：

```powershell
cd E:\AITest\test\baseTest
pwsh -NoProfile -File .\verify-lifecycle.ps1
```

该入口需要PowerShell7.2+并按当前脚本执行构建、测试、格式、SBOM及证据门禁；不是随意访问公司中间件。运行前检查脚本配置，确保使用本地模式。完整成功的报告与源码/制品指纹用于核对本次结果，旧数字不能当作本次验收数量。

Git提交源代码、配置结构、YAML/JSON5、需求追溯和审核证据；target/runtime、密钥与未审核日志不提交。插件不会自动commit/push，Git也不限定GitHub。示例/黄金集只验证固定路径，不证明任意真实需求分析准确。

本次文档合并没有重新触发完整生命周期或真实Kafka/XXL-JOB测试；已有19/22条记录为此前明确范围的针对性测试，不能据此扩大验证结论。

<a id="labs"></a>

## 第六部分：逐步上机实操——八个实验

### 先分清两类实验

- 实验1—7：直接运行框架自身测试，不需要启动订单服务。HTTP日志实验会自行启动随机端口的本地桩并关闭。
- 实验8：运行插件产出的业务测试，需要启动本地订单服务，真实调用HTTP和Dubbo。

先完成前七个， 

以下PowerShell命令均完整给出工作目录。`-Dtest=类名#方法名`整个参数保留引号，避免PowerShell错误处理。相同模块的测试请顺序运行，不要同时启动多个写同一target报告目录的命令。

### 准备：在IDEA打开源码并建立基线

#### 第1步：检查工具

在PowerShell执行：

```powershell
cd E:\AITest\test
java -version
mvn -version
```

使用JDK21和Maven3.9+。Maven输出中的Java home应对应预期JDK。若不一致，先调整本机环境，不要改框架代码规避版本检查。

#### 第2步：打开正确工程

1. IDEA → File → Open。
2. 选择 `E:\AITest\test\baseTest\pom.xml`。
3. 作为Maven工程打开，等待依赖同步。
4. File → Project Structure → Project SDK选择JDK21。
5. Settings → Build, Execution, Deployment → Build Tools → Maven → Runner，JRE选择同一JDK或Project JDK；不同IDEA版本名称可能略有差别。
6. 左侧Project展开base-test-toolkit，再展开src/test/java。

#### 第3步：运行基线

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=DataDriveUtilTest,IdentityContextTest,RuntimeIsolationTest,LifecycleRegressionTest,LifecycleClosureTest,ProtocolCallLogTest,ApiClientLoggingIntegrationTest" test
```

预期BUILD SUCCESS、Failures/Errors为0。该命令只验证选中的框架测试，不是所有模块完整验收。原始报告在 `baseTest/base-test-toolkit/target/surefire-reports`，下一次运行会更新报告，不要依赖旧结果。

此前编写上机实验时已实际运行此基线命令：22条通过，0失败、0错误、0跳过。日志保护测试会故意验证写入失败，可能输出诊断不可用告警；需结合测试断言判断。文中的IDEA断点操作是给你的实操步骤，本次文档合并没有代替你执行GUI逐步点击。

#### 第4步：认识调试操作

1. 点击源码行号左边空白处设置红色断点，放在可执行语句，不放在空行/方法大括号。
2. 点击测试方法左侧绿色三角 → Debug，而不是Run。
3. 暂停后看Debug面板的Frames、Variables、Watches。
4. Step Over执行当前语句；Step Into进入方法；Resume继续到下一个断点。使用界面按钮即可，不依赖个人快捷键设置。
5. 右键断点可以配置Condition；并发实验优先选Suspend Thread，而非暂停所有线程。
6. Watches/Evaluate只读观察值，不调用acquire、close、clear等有副作用的方法，以免改变被调试流程。

### 实验1：亲眼看YAML变成Java参数

#### 打开三个输入文件

在Project中依次打开：

```text
base-test-toolkit/src/test/resources/cases/search.yaml
base-test-toolkit/src/test/resources/cases/search-request.json5
base-test-toolkit/src/test/resources/cases/search-response.json5
```

当前YAML只有一条 `search by keyword`，request引用search-request.json5，expect引用search-response.json5；请求为keyword=coffee、limit=10，期望为code=0、data包含shop-a。

注意users中的test-user在本实验中仅被解析成元数据；被测试的sample方法没有真正执行，因此这里不会自动租账号或登录。

#### 打开入口并设置断点

1. 打开 `src/test/java/io/testkit/basetest/data/DataDriveUtilTest.java`。
2. 找到 `loadsYamlAndJson5Payloads`。
3. 在 `Object[][] rows = DataDriveUtil.loadTestData(method, suite);` 上断点。
4. 再打开 `src/main/java/io/testkit/basetest/data/DataDriveUtil.java`。
5. 在 `load` 方法遍历cases的语句上断点。
6. 在 `toArguments` 中 `Type[] parameterTypes = method.getGenericParameterTypes();` 上断点。
7. 在 `readPayload` 中 `mapper.readValue(input, javaType)` 上断点。

#### Debug单个方法

对loadsYamlAndJson5Payloads点Debug。若想不打断点直接验证，终端执行：

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=DataDriveUtilTest#loadsYamlAndJson5Payloads" test
```

本实验使用显式Path重载，不会经过String suiteName的classpath定位分支。想看包路径定位，留到实验8跟业务类；不要一直等一个本来不经过的断点。

#### 按顺序观察

| 停在哪里 | 展开哪个变量 | 应看到什么 |
| --- | --- | --- |
| 入口调用前 | method、suite | sample方法、cases/search.yaml路径 |
| load循环 | document、testCase | 1条case、name和users |
| toArguments | payloads | 请求文件在前，期望文件在后 |
| 参数遍历 | parameterTypes、index、payloadIndex | 3个参数；CaseContext不消耗文件 |
| readPayload | type、javaType | 第一次Request，第二次Response |
| 回到入口调用后 | rows | 1行3列：Context、Request、Response |

Step Over到rows赋值之后，再展开rows[0]。最后Resume，测试应通过。

#### 这一实验需要写下的答案

1. 为什么3个方法参数只需要2个数据文件？
2. Request与Response按什么规则分配文件？
3. 为什么本实验解析了users却没有调用账号平台？

验收答案方向：CaseContext由元数据产生；非Context参数按request+expect的文件顺序绑定；本实验调用的是解析器而非完整测试执行链。

### 实验2：类型校验、过滤与路径错误

本实验不改仓库原始search文件，而是在IDEA临时运行配置指定另一个工作目录也不合适，因为现有测试使用相对Path。最安全的初学方式：先用断点观察分支；要修改数据时在你自己的练习分支或副本进行，并手工恢复原文，不使用破坏性Git恢复命令。

#### 先做只读类型追踪

1. 打开 `config/StrictTypeModule.java`。
2. 在scalar返回的deserialize方法中，检查token的if语句上断点。
3. 再次Debug实验1的方法。
4. 命中Integer相关路径时，观察type及parser.currentToken，预期数字10是VALUE_NUMBER_INT。
5. 观察mapper构造的位置，确认DataDriveUtil对payload注册了StrictTypeModule。

#### 在自己的副本做三次单变量修改

每次只修改一项，运行实验1命令并记录，再恢复：

| 修改 | 预期现象 | 恢复 |
| --- | --- | --- |
| request中的limit从10改为字符串"10" | Request.Integer字段在本地解析失败 | 恢复数字10 |
| YAML请求文件名改为不存在文件 | 加载payload失败，未发网络请求 | 恢复原文件名 |
| enabled从true改false | 解析rows为0，原测试断言rows.length=1因此失败 | 恢复true |

第三项失败来自“这个单元测试预期1行”的断言，而不是框架禁止禁用用例。要分清被测试行为与外层验证条件。

#### 恢复确认

再次运行实验1方法，必须恢复通过。检查Git diff确认只留下你打算保留的修改；断点不会修改源码内容。

验收：能说出错误发生在“解析前、类型反序列化、还是外层断言”，而不是全部叫用例失败。

### 实验3：跟踪账号租用、身份绑定和归还

#### 打开现有身份测试

打开 `src/test/java/io/testkit/basetest/identity/IdentityContextTest.java`，找到 `accountPoolAndNestedContextAreReleased`。

该方法当前展示基本账号池租用、单层作用域关闭和归还；不要被方法名中的nested误导成它已经演示所有嵌套场景。

#### 设置四处断点

1. 测试方法中的 `UserIdentity acquired = provider.acquire("reviewer");`。
2. InMemoryIdentityProvider.acquire中的 `leased.putIfAbsent(...)`。
3. IdentityContext构造器中的 `STACK.get().push(this)`，以及close中的pop语句。
4. InMemoryIdentityProvider.release中的remove语句。

#### Debug并按状态记录

| 时间点 | leased | IdentityContext栈 | 含义 |
| --- | --- | --- | --- |
| acquire之前 | 空 | 无当前身份 | 未租用、未绑定 |
| acquire返回后 | reviewer-1占用 | 无当前身份 | 租用不等于线程绑定 |
| use之后 | reviewer-1占用 | reviewer-1 | 当前线程可读取身份 |
| scope.close之后 | reviewer-1仍占用 | 空 | 关闭上下文不等于归还 |
| provider.release之后 | 空 | 空 | 才完成账号归还 |

沿代码Step Over/Into，不要在Evaluate里额外调用acquire。原测试末尾还会再次acquire来验证可重新获取；这是测试断言，不是完整企业租约生命周期演示。

不调试时运行：

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=IdentityContextTest" test
```

#### 再看声明如何触发身份

1. 打开PortableTestNgListener.beforeInvocation，找到UseIdentity与context.users的选择逻辑。
2. 打开runtime/LifecycleFixture.java，查看rows中的alice、bob。
3. 暂时不单独运行Fixture。它依赖外层测试初始化共享状态，正确入口是下一实验的LifecycleRegressionTest。

验收：能解释profile与alias、acquire与use、close与release的区别，且知道认证Header尚未在这里发送。

### 实验4：用现有8种场景看完整生命周期

#### 正确入口

打开 `runtime/LifecycleRegressionTest.java`，找到 `failuresDoNotLeakAndSummaryUsesCapturedPublisher`。其DataProvider传入mode0—7，外层测试再创建内层TestNG执行Fixture。

终端运行：

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=LifecycleRegressionTest" test
```

外层8个场景应通过。内层有意制造失败/跳过，不要看到内层失败就直接认定外层回归失败；最终看外层断言是否成立。

#### 对照源码的模式表

| mode | 故意制造的行为 | 内层业务体执行次数 | acquired / released | 外层应验证 |
| --- | --- | --- | --- | --- |
| 0 | 正常 | 2 | 2 / 2 | 2条成功 |
| 1 | Mock.open失败 | 0 | 2 / 2 | 前置失败仍归还身份 |
| 2 | Mock.close失败 | 2 | 2 / 2 | 清理失败被记录，身份仍归还 |
| 3 | identity.release失败 | 2 | 2 / 2 | 归还调用已发生，但平台归还成功不能据此保证 |
| 4 | 原始断言、Mock关闭、归还同时失败 | 2 | 2 / 2 | 保留original assertion并附加清理异常 |
| 5 | publish失败 | 2 | 2 / 2 | 报告发布失败被识别 |
| 6 | acquire失败 | 0 | 0 / 0 | 未获身份，不执行测试体 |
| 7 | 业务体主动Skip | 2 | 2 / 2 | 2条跳过，仍归还 |

这里released只是测试计数器，在抛release failed前先递增，所以2/2不等于远端平台确认归还。这正是学习时必须区分的细节。

#### 第一次只调mode0

1. 在外层测试 `mode = scenario;` 后面的计数器初始化语句设条件断点 `scenario == 0`。
2. 在Listener.beforeInvocation内获取身份的位置设条件 `invoked.isTestMethod() && io.testkit.basetest.runtime.LifecycleRegressionTest.mode == 0`；若IDEA因包访问限制拒绝条件，取消该断点，改在LifecycleFixture.body里用 `mode == 0` 条件观察。
3. Debug外层测试。它会依次运行8种场景，你的断点仅在选定模式停下。
4. 看alice/bob两行的线程、state.identity、Trace和caseId。
5. 在Fixture.verifyScope停下，确认IdentityContext.current为空。
6. 回到外层runner.run之后，观察acquired/released/bodies/records/summaries。

#### 再调mode4

1. 将外层条件改为scenario==4。
2. 在Fixture.body的original assertion语句设条件mode==4。
3. 在Listener.fail的addSuppressed语句设断点。
4. 观察result.getThrowable最初是原始断言，后续Mock/释放异常怎样合并。
5. 在外层断言前查看records与summary，确认有2条失败且原始断言仍可识别。

如果IDEA启用了“遇到所有已捕获异常暂停”，会频繁停在预期异常上；先关闭该全局异常断点，仅使用本实验普通断点。

验收：你能说清失败发生在获取、前置、测试体、清理、发布中的哪一段，以及后续步骤有没有执行。

### 实验5：亲眼看到业务清理逆序且不中断

#### 入口与断点

1. 打开runtime/LifecycleClosureTest.java。
2. 找到 `cleanupRunsEveryCompensationInReverseOrderEvenAfterErrors`。
3. 在 `Assert.expectThrows(... cleanup::close)` 处设断点。
4. 打开CleanupStack.close，在while循环和addSuppressed处设断点。
5. Debug该单方法。

终端验证命令：

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=LifecycleClosureTest#cleanupRunsEveryCompensationInReverseOrderEvenAfterErrors" test
```

#### 逐步观察

1. add按oldest、exception、error注册。
2. close第一项执行error并抛LinkageError，被聚合后继续。
3. 第二项执行exception并抛IllegalStateException，继续。
4. 第三项oldest仍执行。
5. 回到测试断言，released应为 `[error, exception, oldest]`，suppressed长度为2。
6. 再次close不重复执行；关闭后add会拒绝。

验收：能说明“为什么按逆序”“为什么捕获后继续”“为什么最后仍抛失败”，并理解它只覆盖已登记的动作，不能自动补偿尚未登记的prepare部分成功。

### 实验6：并行测试为什么不会把runtime串掉

#### 入口

打开RuntimeIsolationTest.perInstanceRuntimesDoNotRaceDuringParallelRows；再打开IsolatedRuntimeFixture及内部Instance。

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=RuntimeIsolationTest" test
```

#### Debug步骤

1. 在Instance.automationRuntime的return语句设置断点。
2. 在Instance.isolated第一条Assert设置断点，Suspend选Thread。
3. 在RuntimeRegistry.current的返回语句设断点，需要时临时启用，避免每次调用都停。
4. Debug外层测试，分别观察alias为a和b的实例。
5. 查看当前runtime与实例runtime是否同一对象、IdentityContext中的alias是否匹配。
6. Resume直到外层runner.run返回，查看reports映射。

预期reports有a/b两个publisher，每个4条成功。外层测试本身只有1条，不要把内层8条再加到外层Surefire计数里。

验收：能指出RuntimeProvider返回实例runtime，Listener捕获并建立线程scope；不能只回答“AtomicReference是线程安全的”。

### 实验7：请求真实发送了什么，日志又隐藏了什么

#### 使用自带本地HTTP桩

打开http/ApiClientLoggingIntegrationTest，找到 `defaultClientLogsRealBodiesWithoutChangingTheWireOrLeakingCredentials`。

此测试自行启动127.0.0.1随机端口，不占用订单服务18081，不需要手动启动服务。

```powershell
cd E:\AITest\test
mvn -B -ntp -f baseTest/base-test-toolkit/pom.xml "-Dtest=ApiClientLoggingIntegrationTest" test
```

#### 设置三个关键断点

1. `Assert.assertEquals(result.status(), 201)`：观察result、received、metrics。
2. `String text = Files.readString(file);`之后的下一条语句：观察text和file。
3. `Assert.expectThrows`模拟连接失败之后：观察日志ERROR路径。

Debug时核对：

- received中保留虚构RawRequestSecret，说明日志脱敏没有篡改真实请求。
- result.json的token保留虚构RawResponseSecret，说明响应本身没有被脱敏逻辑修改。
- 日志text没有这些秘密，但有SKU-001和201。
- metrics数量为1，说明默认客户端接入当前runtime sink。
- 测试主动关闭随机端口后再次请求，预期连接失败，并有ConnectException诊断。

file是临时目录里的真实日志位置，在Debug里复制绝对路径即可打开；不要固定猜测临时目录名称。

#### 学习runId/caseId/callId

再运行ProtocolCallLogTest.redactsNestedCredentialsHeadersQueriesAndAssociatesTheCase，在 `var rows = Files.readAllLines(file);` 后暂停。

预期两行、一对相同callId；runId为run-test、caseId为case-test、caseName为CASE-001 / create。这里的秘密都是测试虚构值，不要替换为公司真实Token。

验收：分清真实报文、脱敏日志、调用指标、测试结论四种对象。

### 实验8：从生成的业务测试调通HTTP/Dubbo

#### 创建隔离演练工程

```powershell
cd E:\AITest\test\validation\local-http-dubbo-flow-20261018
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Verify-FullFlow.ps1 -Fresh -ConfigOnly
```

记下控制台Fresh projects路径。该脚本准备服务，生成两个新工程并执行故障检测和恢复；完成后确认health的faultyZero=false。别打开旧requirement工程。

#### HTTP：跟到classpath数据加载

1. IDEA打开Fresh目录下http-tests/pom.xml，导入Maven。
2. 打开GoldenOrderHttpTest.cases，在DataDriveUtil.loadTestData调用处设断点。
3. 打开对应suite YAML及6份JSON5，核对名称。
4. 打开src/main/resources/environments/local.properties，确认api.baseUrl指向18081。
5. Debug GoldenOrderHttpTest。
6. 本次进入loadTestData(Method,String)，观察resource为测试包路径加套件名；与实验1的Path重载比较。
7. 在verifyApi观察context/request/expected，再进入API.invoke和ApiClient.request。
8. 看实际target与response.status/json，最后看JsonDiff差异为空。

预期业务类3条通过；完整mvn test包含初始化冒烟时4条。YAML的test环境标签不自动切换ProjectSettings，执行地址仍由test.env选择的文件决定。

#### Dubbo：跟到RPC签名和服务调用

1. IDEA打开同一Fresh目录下dubbo-tests/pom.xml。
2. local.properties确认directUrl为 `dubbo://[::1]:20891`。
3. 打开GoldenOrderDubboTestApi，在client.invoke语句设断点。
4. Debug GoldenOrderDubboTest。
5. 观察接口demo.golden.OrderService、方法create、类型数组java.util.Map、参数数组中一个Map。
6. 进入DubboClient.invoke，观察首次reference.get以及$invoke。
7. 查看返回业务体，不要寻找HTTP201/400字段。
8. 查看服务目录runtime/service-requests.jsonl本轮DUBBO记录，确认提供者确实收到了调用。

此演练无注册中心，一个服务进程同时提供HTTP和Dubbo。Dubbo地址不能在浏览器当HTTP打开。不要在已有服务运行时再启动同端口IDEA实例。

#### 日志与结束检查

查看业务项目target/logs、target/surefire-reports及配置的结果文件。需要查看提供者内部代码时单独调试提供者，注意客户端断点暂停可能引起服务端/客户端超时。

停止服务使用原目录stop-service.cmd；若由IDEA启动则在IDEA停止。只结束你启动且确认归属的实例。

### 每次学习完成必须填的记录

```text
实验名称：
源码版本/本次修改：
运行入口（类/方法或命令）：
断点位置（类/方法/语句）：
观察变量及实际值：
预期行为：
实际行为：
主异常与suppressed：
资源是否全部归还/清理：
报告位置与本轮时间：
输入是否恢复，过滤/断点条件是否清除：
尚未理解的问题：
```

不要把“Debug中看到了对象”当作验证完成；每次最后Resume到结束，确认外层测试结果。不要提交凭证截图、临时日志或只用于实验的过滤条件。

### 推荐今天就做的顺序

先完成实验1、3、4、5：数据怎样进来、身份怎样绑定、失败时怎样归还、业务资源怎样逆序清理。做完后回到本文第三部分的数据驱动、身份、运行时、生命周期与清理章节，你会知道每段代码对应哪个实际现象。其余实验继续补齐并发、日志和真实协议。


<a id="extension"></a>

## 第七部分：业务修改、扩展练习与故障排查

### 练习2A：加一条场景，不加测试方法

1. 找到 src/test/resources/demo/golden/framework/v3/http/golden_order_http_framework_v3.yaml。
2. 复制一条正常用例，改成唯一 name 和 sourceCaseId，例如 LEARN-004。
3. 在同目录新增 learn-004_req.json5、learn-004_exp.json5，内容先与正常场景相同。
4. 修改复制行的 request/expect 引用，保持文件名一致。
5. 再运行业务测试类，预期从3条变4条；完整模块从4条变5条。
6. 核对日志中的场景名，确认不是只改了注释。

### 第三阶段：断言与失败定位

1. 仅把正常场景期望 orderId 改成错误值，运行业务类。
2. 看调用日志：服务仍返回真实正确值；看断言报告：应出现对应字段差异。
3. 说明为什么“HTTP201”与“测试通过”不同。
4. 恢复 orderId，删除期望中的一个响应字段，运行 strict，观察额外字段差异。
5. 阅读 JsonDiff.lenient，理解允许额外实际字段与忽略所有比较不是一回事。
6. 还原期望，恢复全量通过。

验收：能分清请求构造失败、网络失败、业务错误码、断言失败、清理失败。

### 练习4B：在练习工程装配本地身份池

1. 按本文第四部分的示例创建 buyer 身份及 provider。
2. 在 ProjectBootstrap 的 runtime 装配中替换 identities，保持原结果发布器等能力。
3. 在练习测试方法加 UseIdentity("buyer")，在方法内用 IdentityContext.requireCurrent 检查 alias。
4. 移除注解，在一条 YAML 设置 users: [buyer]，验证同样生效。
5. 设置两个 users 且不加注解，验证明确失败，而不是随机选身份。
6. 恢复单用户，观察失败后下一次仍能获取身份，确认 release。

### 练习4C：认证传输单独验证

1. 查看生成 API 用的是 request 还是 authenticated。
2. 用受控的本地鉴权桩或公司的专用测试接口验证 Bearer Header；不要往日志打印真实 Token。
3. 身份上下文存在但 Header 未发送时，应定位 API封装，而不是改身份池。
4. Dubbo 需验证 attachments 的业务协议，不假设 HTTP Bearer 自动迁移。

验收：能解释角色profile与身份alias、acquire与use、close与release的差异，以及内存租用锁不能跨JVM。

### 第五阶段：造数与失败后清理

1. 打开生成的 DataBuilder，标出当前 prepare 仅复制 Map、cleanup 尚无真实平台逻辑。
2. 先做本地练习资源，例如一个内存集合代表本次创建记录，不调用公司真实接口。
3. prepare 中创建唯一资源并返回标识；cleanup 只移除该标识。
4. 在调用前登记 deferCleanup，故意令断言失败，确认资源仍被清理。
5. 再模拟 prepare 创建一半后抛异常，给 prepare 增加内部补偿，验证无泄漏。
6. 模拟 cleanup 抛异常，查看报告，不要吞掉它并继续宣称成功。
7. 理解后再将本地实现替换成 DataFactory 企业造数适配器，补超时、幂等、所有权和TTL。

验收：不能只回答“AfterMethod删数据”，必须解释准备失败、断言失败、进程崩溃这三种不同路径。

### 第八阶段：设计理解与口述

用你刚做过的实际实验回答：

1. 为什么数据驱动放框架、业务造数放项目？举路径错误与造数失败两个例子。
2. 为什么身份provider、context、协议认证要分开？说明换账号平台时哪些不用改。
3. 为什么ThreadLocal不能解决多人CI的账号竞争？说出跨进程租约方案和成本。
4. 为什么日志在协议层实现？说出脱敏、性能、存储与误判风险。
5. 为什么初始化器和插件脚手架不混成一个覆盖所有文件的生成器？说明人工改动保护。
6. 真实模型接入后，如何评估生成用例质量？不能只用“生成了文件”和“样例通过”作指标。

每个回答都用“具体失败 → 分层设计 → 验证证据 → 替代方案与代价”组织，而不是背技术名词。

### 最常见问题定位表

| 现象 | 先检查 |
| --- | --- |
| 框架改了但业务不变 | 是否重新install、是否同版本、业务Maven是否刷新 |
| 依赖找不到 | 本地仓库或批准私服是否有对应版本，不是插件模型问题 |
| 只执行1条成功 | 是否仅跑初始化testng.xml，检查扫描包与业务类 |
| suite not found | 类包、suite名、资源目录、Maven资源复制 |
| payload数量错误 | 方法非CaseContext参数数与request+expect文件数 |
| 地址改了没生效 | test.env、TEST_ENV、IDEA VM options、target/classes与源码区别 |
| No identity provider | runtime没有装配provider，或使用了错误runtime |
| No identity is available | profile/alias、账号池容量、是否归还、独立provider实例 |
| 有身份但未认证 | API是否调用authenticated或设置正确RPC附件 |
| 造数没有真实效果 | 是否还在默认复制Map、DataFactory是否未配置 |
| Dubbo连接失败 | 服务20891是否监听、IPv6地址、接口签名、group/version |
| 日志没有正文 | bodies、环境、敏感/非JSON处理、是否使用新框架包 |
| 代码生成冲突 | 人工修改保护与manifest，不删除清单强制覆盖 |
| 自定义PRD仍生成订单 | 黄金集仍开着；真实模型另行配置并评估 |

### 学完后应交付什么

保留一份自己的工程目录说明、一条完整用例的执行链笔记、一次数据驱动加例记录、一次身份租用释放验证、一次失败后清理证明、一组真实HTTP/RPC日志和一份未接入能力清单。

这些比“看完全部Java类”更能证明你真正理解框架。后续再读企业模式、分片、资源租约及真实中间件适配，避免第一天把所有模块同时接到公司环境。

<a id="finish"></a>

## 第八部分：学习验收、实验记录与能力边界

### 你最终要能完成的事情

1. 不看文档，从YAML跟到Java方法参数，再跟到真实HTTP/RPC请求。
2. 区分角色池、账号租约、线程身份、认证传输与平台授权。
3. 验证正常、前置失败、断言失败、清理失败、发布失败时的资源和结果。
4. 解释同JVM线程隔离、跨线程传播、跨进程租约各解决什么问题。
5. 在不修改所有测试方法的前提下接入身份/造数平台，并给出超时、补偿、幂等和安全方案。
6. 能定位故障发生层，不靠关闭断言、强制覆盖或无限重试“修复”。

### 每次实操的记录模板

```text
实验名称与源码版本：
操作目录与测试入口：
断点（类/方法/语句）：
观察变量与实际值：
预期行为与实际行为：
主异常与suppressed：
账号、业务数据、线程作用域是否归还/清理：
本轮报告文件、时间与执行数：
输入/过滤/断点条件是否恢复：
尚未理解的问题与下一步：
```

### 如何读本文中的测试数字

19条是源码手册原先列出的特定测试集合；22条是上机实验基线的另一个测试集合。它们是此前实际执行记录，不是全框架当前总数，不是本次文档合并时重新执行的结果。完整验收以当轮报告为准。嵌套TestNG故意失败后由外层断言验证，与顶层构建失败不是一回事。

本地HTTP和真实Dubbo直连已有完整流程复验记录，但注册中心、公司鉴权、真实造数平台、Kafka/XXL-JOB远程联调、IDEA逐按钮GUI与远程模型语义质量不能据此自动算通过。

### 一句话总结设计

测试资产表达场景，测试类编排意图，运行时装配能力，身份/数据/协议按各自边界工作，日志解释调用，报告说明结论，生命周期与租约管理资源。你应能用具体失败、源码路径和实验结果解释每一层，而不仅是背组件名称。
