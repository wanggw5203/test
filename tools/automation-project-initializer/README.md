# 自动化项目初始化工具 1.0.1

逐步操作与上下游衔接见 [三个工程完整操作手册](../../docs/使用与学习/三个工程完整操作手册.md)。

1.0.1 增加框架调用诊断日志配置：local/test 开启脱敏正文，pre 关闭正文；输出控制台和 target/logs/api-calls-${pid}.jsonl。公共能力在框架中实现，模板只提供配置，测试方法不重复打印。使用此配置需更新框架到包含 ProtocolCallLog 的构建。

选择 HTTP、DUBBO、SDK 类型，自动生成引入当前 TestNG 框架的 Maven 自动化工程。
这是独立模块，模板、版本及测试独立维护；IDEA 插件只提供调用入口，不把初始化规则混入需求/接口生成规则。
不依赖模型，不要求用户预先准备另一个初始化工程。

## IDEA 使用

安装 AI Test Flow 1.6.1，打开侧栏“流程与配置”，点击“创建自动化项目（HTTP / Dubbo / SDK）”。
填写类型、目标绝对路径、Maven 坐标、Java 包名、框架版本，点击“生成项目”。
然后通过 IDEA 的“文件 → 打开”打开生成目录并导入 Maven。当前不是原生 New Project 向导，也不会自动关闭当前项目。

## 独立使用

初始化工具使用 JDK 17+，生成工程使用 JDK 21。

```powershell
java -jar dist/automation-project-initializer-1.0.1.jar --type HTTP --directory E:/AITest/my-http-tests --artifact-id my-http-tests --group-id com.example --package com.example.automation
```

`--type` 改成 DUBBO 或 SDK 即使用对应模板。`--framework-version` 可选，默认 1.1.0-SNAPSHOT。
不自动安装依赖：框架须已发布到可访问的 Maven 仓库，或预先在本仓库 baseTest 下执行 `mvn -DskipTests install`。
不写入虚构的私服地址、账号、密钥。不自动初始化或提交 Git。

## 产物

- pom.xml：框架 compile 依赖；Dubbo 类型额外包含 enterprise-adapters 与 Dubbo 运行依赖。
- main：环境加载、TestNG 启动入口、项目 bootstrap、JSONL 报告、类型对应的基础封装及 data 扩展目录。
- main/resources：local/test/pre 环境配置、TestNG 自动加载启动监听器的 ServiceLoader 配置。
- test：继承 BaseTest 的初始化验证测试、DataDriveUtil YAML 用例和 JSON 测试数据、testng.xml。
- .ai-test-flow/automation-project.yaml：插件识别项目的类型、框架版本、目录约定。
- quality-assets/inputs：项目内的需求材料入口；后续 TAD、测试用例、脚手架由插件生成。

运行 `mvn test` 验证 bootstrap → BaseTest → YAML/JSON → 断言 → 报告；用 `-Dtest.env=test` 切换环境。
默认验证不会调用真实业务服务。Java main 可在包含测试输出的类路径中启动 TestNG；通常优先使用 mvn test。

SDK 模板仅生成通用 SDK 适配扩展点，需提供真实 SDK 坐标、客户端、鉴权与释放逻辑。当前插件接口自动生成仍支持 HTTP/Dubbo，不将 SDK 工程初始化等同于 SDK 方法自动发现/用例生成。

## 安全与边界

可以使用新目录，也可以在无冲突的现有目录中生成；已有 Maven/Gradle 工程一律拒绝重新初始化。
生成前检查所有文件冲突，不覆盖已有源文件和 README；保留已有 .gitignore。
生成时使用 CREATE_NEW，失败时仅清理本次创建且未修改的文件，保留目录和人工内容。
目前提供 Maven/Java/TestNG 模板；不宣称覆盖所有构建系统、企业私服或真实业务适配器。
