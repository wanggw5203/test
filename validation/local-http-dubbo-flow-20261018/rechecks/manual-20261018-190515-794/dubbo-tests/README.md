# local-order-dubbo — DUBBO 自动化项目

由独立 automation-project-initializer 1.0.1 初始化；插件的接口/用例生成规则与此工具独立维护。

## 启动

使用 JDK 21。先将 io.testkit:base-test-toolkit:1.1.0-SNAPSHOT 发布到可访问的 Maven 仓库；Dubbo 类型同时需要 enterprise-adapters。
当前模板不会携带框架源码、安装框架或伪造仓库地址。若框架只在本仓库中，可先在 baseTest 目录执行 mvn -DskipTests install。

运行 `mvn test` 验证初始化；使用 `mvn test -Dtest.env=test` 切换环境。默认用例只验证启动、YAML/JSON 和 BaseTest 链路，不调用 HTTP、Dubbo 或 SDK 业务服务。
`bootstrap.AutomationMain` 是可选 TestNG 启动入口，运行它时需包含 test 编译输出与资源；不应只用 main 类路径运行。
`src/test/resources/testng.xml` 可用于 IDEA TestNG 运行；mvn test 使用标准 *Test/*Tests 命名发现测试类，不限定业务包。

## 分层

- main：bootstrap、环境加载、公共传输封装与业务数据扩展。
- test：测试方法、YAML 用例、JSON 测试数据。
- .ai-test-flow/automation-project.yaml：项目类型、框架版本及目录约定，交给插件读取。
- quality-assets：项目内维护需求、TAD、用例和接口证据，并用 Git 版本管理。工具不会自动 git init、commit 或 push。

ProjectBootstrap 由 TestNG ServiceLoader 自动加载，安装 JSONL 报告和项目运行时；真实身份、造数和发现 Provider 由项目接入，不会默认声称可用。
环境配置不包含密钥，JVM -D 参数优先。真实 Dubbo 调用前需设置 registry 或 directUrl；SDK 项目需补充真实 SDK Maven 坐标、客户端和生命周期适配。
插件当前接口脚手架支持 HTTP/Dubbo；SDK 模板目前提供基础工程与通用适配扩展点，不代表已支持 SDK 方法自动发现和生成。

在 IDEA 中打开本目录并导入 Maven 后，使用 AI Test Flow 插件继续 PRD → TAD → 测试用例 → 接口脚手架。
