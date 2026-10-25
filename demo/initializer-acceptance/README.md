# 独立项目初始化验收样例

http、dubbo、sdk 三个目录由 automation-project-initializer 1.0.0 自动创建，未手工编排 POM。
三种类型的初始化验证均实际执行通过，HTTP Java main 启动入口也通过验证。

随后在 http/dubbo 中追加了同源接口脚手架生成器的示例接口产物，以验证两套规则可以组合：

- http：对本地测试服务执行，初始化验证 + 请求/期望数据驱动接口测试，共 2 条通过。
- dubbo：新增接口骨架编译通过；没有连接真实 Dubbo 服务。
- sdk：初始化验证通过，通用 SDK 适配扩展点可编译；未配置真实 SDK 坐标或调用真实 SDK。

注意 http 中已追加接口测试，重新执行 mvn test 需要本地 HTTP 服务：POST /api/orders，输入 sku=SKU-001、quantity=1，返回 200 和 {"success":true}，通过 -Dapi.baseUrl 指定地址。
dubbo 中已追加真实调用骨架，缺少 registry/directUrl 时不能将全量 mvn test 视为可离线通过；只做初始化验证可用 -Dtest=InitializationSmokeTest。
新创建的空白模板只含离线初始化验证，不会发起业务调用。
