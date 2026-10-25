# 企业自动化框架工程规范

本规范约束框架维护者、协议适配器维护者和业务测试作者。目标是让多人协作时，名称能说明职责，代码能审查，升级可迁移，测试结果可追责。文档里的“新增”规则不代表已经批量改写历史 API；已有 Maven 坐标、包名、公开配置键和稳定用例 ID 按兼容契约保留。

## 1. 模块和职责边界

| 模块 | 职责 | 新增代码的放置规则 |
| --- | --- | --- |
| `testkit-parent` | JDK/Maven 准入、依赖及插件版本、构建质量门禁 | 统一规则只在父 POM 维护，子模块按需补自身配置 |
| `testkit-bom` | 向独立业务项目提供依赖版本对齐 | 不绑定业务项目自身版本，不放业务测试 |
| `base-test-toolkit` | 测试生命周期、协议无关契约、鉴权/身份/上下文、断言和数据能力 | 不反向依赖 Kafka、Dubbo、XXL-JOB 实现 |
| `enterprise-adapters` | Kafka、Dubbo、XXL-JOB 等平台适配 | 平台和版本差异封装在适配器/SPI 中，不渗入业务用例 |
| `java-api-lab` | 可本地部署的演练服务及其测试 | 示例账号、内存数据和本地协议桩不作为公司环境配置 |
| `enterprise-team-example` | 业务团队接入和规范示例 | 演示元数据、并发隔离、SDK/任务/消息断言；不承载基础框架实现 |

保留 `io.testkit` Maven groupId 和 `io.testkit.basetest` 已发布包名。本轮规范化使用可读的 Maven 展示名称；展示名称不会改变依赖坐标。真实公司接入时，业务包使用公司自有反向域名和领域名，不直接把演示包 `io.testkit.team` 扩散到所有团队。

## 2. Java 命名、格式和可读性

- 类、接口、记录、注解使用 `UpperCamelCase`，表达职责；方法和变量使用 `lowerCamelCase`，布尔判断优先使用 `is`、`has`、`can` 等明确语义。
- 适配器采用 `<Platform><Capability>`，例如 `JdbcResourceLeaseProvider`。版本差异通过版本 SPI 或明确版本适配器表达，不使用 `NewClient`、`ClientV2Final`、`CommonUtil2`。
- 生产代码使用完整业务名，例如 `resourceKey`、`ownerToken`、`fencingToken`、`tableName`；循环下标、局部 lambda 可以使用简短名称。不得把多个独立字段或语句压在同一行。
- 新增及重构代码禁止通配 `import` 和未使用导入，历史代码逐步迁移。Java 格式由父构建的 Spotless 和固定版本 google-java-format 统一；`verify` 检查，提交前执行 `mvn spotless:apply`。Spotless检查格式；命名语义、职责和导入规范仍需评审，不宣称已由格式器检查全部规则。
- 公共 API 写明职责、参数含义、默认值、线程语义、资源归属和失败方式。注释解释约束和原因，避免重复翻译方法名。
- 不为“名字看起来统一”批量破坏成熟 API。已有 `CaseDefinition` 是验证后的逻辑用例定义，`RunMetadata` 是运行身份，`EnterpriseExecution` 是运行身份/准入策略/分片的组合，`Shard` 是确定性分片坐标；它们不是同一层对象，不合并成通用 `Context` 或 `Manager`。
- 不从业务测试直接访问 `org.testng.internal` 等内部实现；框架内部测试需要模拟引擎时应限定使用范围。

## 3. 用例命名与元数据

新增测试类使用 `<Domain><Behavior>Test`；方法使用 `should<ExpectedBehavior>`，有关键条件时补 `When<Condition>`。参数化数据提供器采用 `<Domain><Scenario>Scenarios`，每一行使用稳定场景 ID。

```java
public final class OrderWorkflowTest extends BaseTest implements EnterpriseRuntimeProvider {
    @Test(dataProvider = "orderQuantityScenarios")
    @TestCaseMetadata(
        id = "orders.sdk-price",
        owner = "orders-qa",
        team = "orders",
        service = "order-api",
        level = CaseLevel.SMOKE)
    public void shouldCalculateOrderTotal(String scenarioId, int quantity) {
        // Arrange: 构造当前调用的隔离数据。
        // Act: 通过框架客户端执行操作。
        // Assert: 断言业务结果，并由作用域清理临时资源。
    }
}
```

`id` 是跨重命名、分片和历史报告使用的稳定逻辑身份，不是 Java 方法名或运行 UUID。新用例建议采用 `<domain>.<capability>.<expected-outcome>`，例如 `orders.payment.reject-expired-token`；历史 `orders.sdk-price` 等 ID 保持原样。ID 只能包含 ASCII 字母、数字、点、下划线和连字符，首字符为字母或数字，长度 1–128。

`owner`、`team`、`service` 使用稳定目录标识，不能填“张三临时用”“test1”或凭证。它们与运行环境/团队标识使用 1–64 位同类安全字符；配置和策略的标识必须完全一致。`owner` 应能映射到公司团队或角色目录，框架自身不提供这份目录。

在类上声明默认归属，在方法上声明明确稳定 ID。方法的非空 owner/team/service 覆盖类默认；方法存在注解时使用方法 level，默认仍为 `REGRESSION`；destructive 为类和方法的逻辑或，方法不能解除类的破坏性限制。

测试需要独立运行，不依赖方法执行顺序。分片时拒绝 TestNG 方法/分组依赖；同一个逻辑方法的全部 DataProvider 行留在同一分片。断言异步结果时通过关联键和有界等待观察最终结果，禁止靠固定长时间休眠猜测完成。

## 4. 兼容迁移清单

| 原入口/名称 | 规范入口/名称 | 兼容规则 |
| --- | --- | --- |
| `@CaseInfo` | `@TestCaseMetadata` | 旧注解已 deprecated，`forRemoval=false`；当前版本继续支持，不要求一次迁完 |
| `OrderTeamTest` | `OrderWorkflowTest` | 只改演示类名，不改 Maven 坐标、公开框架类型或稳定用例 ID |
| `sdkPrice` | `shouldCalculateOrderTotal` | 保留 `orders.sdk-price` |
| `sdkContext` | `shouldPropagateInvocationContextToSdkWorker` | 保留 `orders.sdk-context` |
| `localJobCompletesOwnFixture` | `shouldCompleteOrderSettlementJob` | 保留 `orders.local-job` |
| `localMessageMatchesOwnFixture` | `shouldConsumeMessageForCurrentOrder` | 保留 `orders.local-message` |
| `orderRows` | `orderQuantityScenarios` | 同步更新 `@Test(dataProvider=...)`，保留每行场景 ID |

一次迁移一个声明，只替换注解类型，字段值保持一致；禁止在同一个类或方法上同时声明两种注解，解析器会直接拒绝。允许父类旧注解、子类新注解，按距离最近的类声明解析；也允许类旧注解、方法新注解。

若旧用例通过“类 ID 前缀 + 方法名”自动生成逻辑 ID，在改方法名前先显式固定为原来的完整 ID，再改 Java 名称。本轮演示用例原本已经有显式 ID，因此重命名不会改变分片或报告归属。删除旧 API、改变分片算法或字段语义必须进入下一次明确的破坏性版本迁移，并提供兼容说明及验收记录。

## 5. 配置、环境和凭证

- 已公开的 `test.env`、`testkit.runId`、`testkit.workerId`、`testkit.team`、`testkit.shard.index`、`testkit.shard.count` 等键保持兼容，不凭风格偏好新增一套等价别名。
- 新配置使用 `testkit.<domain>.<lower-kebab-property>`，例如未来的 `testkit.execution.max-concurrency`；同一模块不得混用连字符、下划线和驼峰来表达同一新属性。该示例是命名约束，不表示已经实现该配置。
- 环境变量使用 `UPPER_SNAKE_CASE`，只支持代码中明确实现并记录的映射；不能假定任意 Maven 属性会自动映射到环境变量。
- 布尔、枚举、数量、持续时间和路径必须在入口验证。持续时间在 Java API 中使用 `Duration`，配置必须在名称或文档中说明单位；禁止负数、溢出、无界重试或含混的 `timeout=30`。
- 客户端环境按 `test.env`、`TEST_ENV`、`local` 解析；可选 `testkit.environment` 只做一致性验证。不得运行的是 qa 用例、连接的却是 local 或 prod 服务。
- HTTP origin 白名单按 `environment/service` 分组，显式列出 scheme、host、port；不能包含账号、路径、查询和片段。允许的端口为 1–65535。白名单是运行准入，不代替 IAM、路径权限或网络策略。
- 凭证来自本机未提交配置、环境变量或 CI 凭证管理；报告和 artifact 只保留允许字段。账号密码、Token、连接串不得写进稳定 ID、owner、资源键或元数据。

## 6. 生命周期、共享资源和失败语义

运行顺序应能清楚对应到：配置解析 → 用例发现/元数据校验 → 准入与分片计划 → 身份及数据准备 → 执行与有界等待 → 业务断言 → 资源清理 → 结果发布 → CI 完整性门禁。

企业上下文是显式线程作用域，必须在创建线程按后进先出关闭；异步执行显式捕获/重绑快照，不用全局可变身份或可继承线程变量。数据键包含 run/worker/team/environment/service/case/invocation 的散列，防止并发用例互串；命名隔离不等于外部数据已经清理。

共享账号和其他稀缺资源通过租约协调。`tryAcquire` 返回空表示资源竞争，数据库或网络异常必须抛出；禁止把基础设施故障伪装成“没有获取到资源”。JDBC 租约使用数据库时间，调用方负责续期，目标业务系统需要校验 fencing token 才能阻止过期持有者继续写入。禁止未经真实目标数据库验收就宣称跨节点强一致保障。

Manifest 写入前校验完整计划以及确定性分片归属，不接受漏选或跨分片选取。计划 hash 仅覆盖逻辑 ID，CI 仍要固定所有 worker 的源码、依赖和数据版本。运行中的配置、清理和结果发布失败应显式进入失败记录；CI 不得只看某个业务断言通过就宣布成功。

## 7. 评审与发布准入

每次框架修改应同时说明：触发场景、最终行为、兼容影响、验证结果及真实环境覆盖边界。涉及协议差异、鉴权、线程上下文、清理、结果一致性或分片时，补能证明边界的回归测试；不为单纯注释和可逆样式调整堆砌镜像测试。

合入前执行根 `verify-framework.ps1` 完成构建和报告验收，并验证报告/分片门禁自身的拒绝路径。不同 worker 的报告合并必须核对 runId、workerId、environment、team、完整计划和分片覆盖。Maven 构建成功、JSONL 文件存在、报告中有通过用例均不能单独证明闭环完整。

发布到企业私服前固定发布版本，记录源码提交、依赖清单、兼容变更和迁移步骤；快照用于集成，正式版本按团队发布策略推广。真实平台、公司 IAM、私服权限、数据库和分布式容量的验收记录单独保存，本地协议桩的通过结果不能替代它们。
