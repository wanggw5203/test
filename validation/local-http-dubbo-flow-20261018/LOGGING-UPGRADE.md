# 框架调用日志升级验收

日志已补入框架 ApiClient / DubboClient，不需要在测试类中手动打印。框架完整说明见仓库 `baseTest/base-test-toolkit/PROTOCOL-LOGGING.md`。

## 现在如何运行

本机 Maven 仓库中的 base-test-toolkit、enterprise-adapters 1.1.0-SNAPSHOT 已更新。IDEA 中重新加载 Maven、重新构建后运行原用例即可；本地两个模块已配置地址及日志选项，不要求额外填写地址启动参数。无需重启被测服务。

```properties
testkit.log.enabled=true
testkit.log.console=true
testkit.log.bodies=true
testkit.log.file=target/logs/api-calls-${pid}.jsonl
testkit.log.maxBodyChars=4096
testkit.log.maxFileBytes=10485760
```

配置在各模块 `src/main/resources/environments/local.properties`。框架默认只输出摘要，模板通过配置显式开启脱敏正文。关闭正文用 bodies=false，关闭诊断日志用 enabled=false；测试结果报告不会因此关闭。

## 实际验收

- 框架核心 77 条、适配层 24 条测试通过，包括脱敏、嵌套 JSON、截断、轮转、并发 JSONL、日志 IO 失败不影响调用、实际 HTTP/Dubbo 传输及既有框架回归。
- 本地 HTTP：不加地址启动参数，直接 mvn test，4 条通过，控制台与文件均记录 3 组 REQUEST/RESPONSE。
- 本地 Dubbo：不加地址启动参数，直接 mvn test，4 条通过，同样记录 3 组 REQUEST/RESPONSE。
- HTTP 默认构造器不再使用 noop 指标接收器，动态连接当前项目 RuntimeRegistry 的 invocationLogs；原 JsonLinesResultPublisher 仍保持不携带正文的结果格式。

控制台日志以 `[TESTKIT CALL]` 开头，包括目标、方法、脱敏请求/响应、HTTP 状态、耗时、runId/caseId/caseName/callId。相同 callId 配对一次调用；业务断言的通过/失败仍看 TestNG 报告，不把 HTTP 400 本身误当成传输异常。

每个模块的 `quality-assets/evidence/logging-upgrade-20261018/maven.log` 保存实际控制台结果，`protocol-calls.jsonl` 保存直接 UTF-8 文件日志快照；实时文件在各模块的 target/logs 中。

## 安全边界

敏感字段递归脱敏，原始请求/响应不被修改；非 JSON 和无法安全序列化的正文不会原样输出。日志不能自动识别所有业务秘密，使用 redactKeys 补充专有字段，避免把秘密放进 URL 路径或用例名。文件为 UTF-8；IDEA 控制台建议 UTF-8。

框架此次仍使用开发版 1.1.0-SNAPSHOT，团队使用前需要发布匹配的新 core/adapters 到共享 Maven 仓库并刷新缓存；不能假设旧缓存已经包含此能力。初始化工具 1.0.1、插件 1.6.1 已同步配置规则，旧项目只需更新框架及自己的环境配置，无需重新生成测试代码。
