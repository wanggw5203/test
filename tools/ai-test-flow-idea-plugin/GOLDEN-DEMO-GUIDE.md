# 黄金集流程验证（插件 1.6.0）

模型入口默认启用黄金集 `order-create-v1`。TAD、用例、接口规格、Web ATC 和反馈均使用插件内置的固定响应，不发送网络请求，也不需要 API Key。自定义输入不会改变固定响应；正式分析时关闭黄金集 Demo。

## 操作

1. 从磁盘安装 `ai-test-flow-idea-plugin-1.6.0.zip` 并重启 IDEA。
2. 若还没有自动化工程，先在“流程与配置”中选择“创建自动化项目（HTTP / Dubbo / SDK）”，创建 HTTP 或 Dubbo 模块，再在 IDEA 打开生成目录并导入 Maven。初始化规则独立维护；之后的接口资产生成不会改写构建文件和公共配置。
3. 点击“载入黄金集 Demo”。默认选中 HTTP，并填入 `POST /demo/orders`、PRD 和演练证据。
4. 点击“运行完整链路（全部资产）”。也可按 1、2、3A、4、3B、5 分步执行。
5. 查看运行日志和项目文件变化，并用 Git 检查、评审、提交。插件不自动 commit 或 push。

验证 Dubbo 时，先切换协议为 DUBBO，再点击“载入黄金集 Demo”，会填入 `demo.golden.OrderService#create(java.util.Map)`，然后运行完整链路。

## 应看到的结果

- 项目输入和证据：`quality-assets/inputs/`、`quality-assets/evidence/`。
- 结构化 TAD：`quality-assets/analysis/`，包含 BR-001 至 BR-003。
- 用例：`quality-assets/testcases/`，包含 GOLD-001 至 GOLD-003。
- 接口规格、Web ATC、反馈：`quality-assets/automation/`。
- HTTP/Dubbo Java：`src/test/java/demo/golden/framework/v3/http/` 或 `src/test/java/demo/golden/framework/v3/dubbo/`，包含 TestNG 测试类；API 类、DataBuilder 类位于 `src/main/java/` 的同名包中。
- 套件 YAML 和 6 个请求/期望 JSON5：`src/test/resources/demo/golden/framework/v3/` 对应协议目录。
- 生成清单、加工计划与接口 testng.xml：`.api-test-generator/golden_order_http_framework_v3/` 或 `golden_order_dubbo_framework_v3/`。不生成验证 POM。新包名和编号避免覆盖旧版骨架。
- 追溯：`.ai-test-flow/manifests/`、`.ai-test-flow/runs/`，模式为 GOLDEN_DEMO，记录固定黄金集版本。

三条场景为正常下单（HTTP 201）、数量为零（HTTP 400）、缺少商品编码（HTTP 400）。Dubbo 返回同一业务体，不使用 HTTP 状态码。

反馈中的 `blocked / not-run` 是正确结果：本次验证资产流程，没有执行真实服务、浏览器或 TestNG。Web ATC 的定位器保留待确认。

## 重复演练

已有脚手架会触发覆盖保护。再次跑同一协议的全链路时，请使用另一个干净的演练项目；原项目可继续单独运行 TAD、用例或反馈阶段。插件不会删除或强制覆盖旧源码与数据。

## 切换真实模型

在 Settings → Tools → AI Test Flow 取消“黄金集 Demo”，设置模型网关、模型和 API Key。所有阶段共用这份配置。对于旧项目曾经关闭演练模式的情况，点击“载入黄金集 Demo”会重新开启，无需改动现有模型地址与密钥。
