# 插件 1.6：初始化与测试资产生成的分工

## 三方职责

- 自动化框架：公共能力，通过依赖供业务项目使用。
- 独立项目初始化模块（已实现）：选择 HTTP/Dubbo/SDK 后创建项目、构建文件、框架依赖、公共启动、环境配置及执行入口；可通过独立 CLI 使用，也可由插件的“创建自动化项目”按钮调用，无需用户自行准备其他工程。
- IDEA 插件：需求整理为 TAD/TRD/STD、生成测试用例、读取接口证据，生成业务接口封装、造数扩展、TestNG 测试代码、YAML/JSON5 数据以及追溯清单。所有业务资产在项目内 Git 管理，不自动提交或推送。

## 项目握手（由独立初始化规则生成，接口资产生成阶段只读取）

当前打开的目录应是已经初始化的 Maven/Gradle 自动化模块。框架必须在 main 编译类路径上；仅 test scope 不够。普通 Maven 模块可直接识别 `io.testkit:base-test-toolkit` 的 compile 依赖声明。父 POM、属性化坐标及 Gradle 模块使用以下项目约定声明，由初始化工程负责保证声明与真实构建一致：

文件：`.ai-test-flow/automation-project.yaml`

```yaml
schemaVersion: 1
framework: io.testkit:base-test-toolkit
frameworkScope: compile
mainSourceRoot: src/main/java
testSourceRoot: src/test/java
testResourceRoot: src/test/resources
```

目录字段可以省略，默认采用 Maven 标准结构；配置不得越出当前项目。非标准目录优先使用项目约定，而不是让模型决定。目录下的 Java/资源包名继续由 generation-spec 的 packageName 决定。为兼容框架 DataDriveUtil，resourcePackage 必须和 packageName 一致，不能任意将 YAML 再移到 cases 子目录。YAML 和 JSON5 仍位于 test 资源包内。

该检查是依赖声明检查，不会执行依赖下载或证明远端仓库可用；真正的类路径兼容性由业务项目编译验证。Dubbo 还需初始化工程配置 enterprise-adapters 及其 Dubbo 依赖。

## 生成内容

| 位置 | 插件生成 |
| --- | --- |
| main Java 业务包 | `<ClassName>Api.java`、`<ClassName>DataBuilder.java` |
| test Java 业务包 | 继承 BaseTest、使用 CaseContext/DataDriveUtil/JsonDiff 的 TestNG 测试类 |
| test resources 对应包 | 用例 YAML、分环境请求及期望 JSON5 |
| `.api-test-generator/<apiCode>/` | 单接口 testng.xml、扩展说明、可执行化计划、校验清单 |
| quality-assets | PRD/TAD、上游用例、接口规格、证据及反馈 |

单接口 testng.xml 仅列举本次测试类，不替代项目公共执行入口。项目公共运行时和环境加载仍由初始化工程维护；API 适配类消费既有的系统属性，例如 api.baseUrl、dubbo.registry 和 dubbo.directUrl。真实身份、业务造数、BO/VO 的映射必须以真实项目源码和配置为准；当前 DataBuilder 生成的是明确 TODO 扩展点，不伪造已接通的业务能力。

## 操作与保护

1. 安装插件 1.6.0 并重启 IDEA。若没有自动化模块，先在“流程与配置”中点击“创建自动化项目（HTTP / Dubbo / SDK）”，再通过 IDEA 打开生成目录并导入 Maven；也可使用独立初始化 CLI。
2. 从项目内输入文档生成 TAD，再生成包含唯一 caseId 的测试用例。
3. 选择 HTTP/Dubbo 接口并提供证据，生成接口规格，再生成脚手架。界面生成会核对规格引用的 sourceCaseId 是否存在于上游用例中。
4. 检查 main/test 产物、Git diff 和计划中的待办，使用项目自己的构建入口编译、执行。真实执行前确认目标环境和测试数据风险。

不存在项目构建文件时，接口资产生成阶段停止并提示先运行初始化入口；需求分析和用例生成不受此限制。接口路径/编码发现 SPI 保留，当前没有真实项目时不推断调用链或类型。SDK 类型现已支持工程初始化和通用扩展点，SDK 方法的自动发现/脚手架生成尚未实现，不应使用 HTTP/Dubbo 规格假装 SDK 用例。

重复生成只复用实际输出相同且校验未变化的产物。人工修改、目录约定变化、旧版生成结构不一致时停止，不删除、不覆盖、不自动迁移。黄金集使用 framework_v3 的新接口编号和包名，避免覆盖旧版 v1/v2 资产；自定义项目需要自行评审迁移或使用新的接口版本及不冲突的类/数据文件名。

## 验收范围

插件自动测试覆盖黄金集各阶段门禁、HTTP/Dubbo 资产生成、上游用例映射、重复生成、人工修改保护与模型网关调用。`demo/plugin-v15-acceptance` 是独立手工维护的外部项目验收夹具，其 POM 不是插件生成物；用于验证 main/test 分离后可编译，并使用本地 HTTP 服务验证数据驱动调用。真实 Dubbo 服务执行、真实业务造数和真实接口发现仍需接入实际项目后验证。未进行新版 IDEA 界面的手工回归时，不将自动测试等同于 GUI 验收。
