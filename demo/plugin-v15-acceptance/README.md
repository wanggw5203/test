# 插件 1.5 生成器编译与执行验收

这里是外部初始化工程的验收夹具，POM 与项目约定由人工维护，不是插件产物。
http、dubbo 的 main/test 代码与资源由插件同源生成器生成。

依赖框架已安装到 Maven 仓库，Java 21。

- 编译两种协议：`mvn -f demo/plugin-v15-acceptance/pom.xml test-compile`
- HTTP 执行：在本地启动返回 `{"success":true}` 的 HTTP 测试服务后，运行 `mvn -f demo/plugin-v15-acceptance/pom.xml -pl http test -Dapi.baseUrl=http://127.0.0.1:18765/`
- Dubbo 此处只做编译验收；没有真实服务时不会宣称执行通过。

演示接口是 POST /api/orders，输入 sku=SKU-001、quantity=1，期望 HTTP 200。请求/期望来自 test resources 的 YAML/JSON5，而非硬编码测试参数。
