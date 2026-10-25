# HTTP / Dubbo 调用诊断日志

框架内置 ProtocolCallLog，在 ApiClient 和 DubboClient 中自动记录 REQUEST、RESPONSE 或 ERROR，不需要测试方法自行打印。现有构造器和 InvocationRecord 接口保持兼容。

## 配置

这些设置可以写入自动化项目的 `src/main/resources/environments/local.properties`，由 ProjectBootstrap/ProjectSettings 加载；JVM `-D` 仅用于临时覆盖，不是必需条件。

```properties
api.baseUrl=http://127.0.0.1:18081/
dubbo.directUrl=dubbo://[::1]:20891
testkit.log.enabled=true
testkit.log.console=true
testkit.log.bodies=true
testkit.log.file=target/logs/api-calls-${pid}.jsonl
testkit.log.maxBodyChars=4096
testkit.log.maxFileBytes=10485760
testkit.log.redactKeys=businessPrivateField,业务隐私字段
```

框架默认启用调用摘要，默认关闭正文；初始化工具 1.0.1 的 local/test 模板显式开启脱敏正文，pre 模板关闭正文。自行使用框架但没有项目配置加载器时，可由自己的 bootstrap 设置对应系统属性。

- enabled=false：关闭诊断日志，既有结果/指标发布不受影响。
- console=false：关闭控制台输出。
- file 设为空：关闭文件输出。`${pid}` 在运行时替换为 JVM PID，避免不同测试进程共写一个文件。
- bodies=false：不输出 body 和 RPC arguments，仍保留方法、目标、耗时等摘要。
- maxBodyChars：脱敏后单个字段值的上限，范围 128–65536；超出以带 `_truncated` 的 preview 表示。
- maxFileBytes：单文件轮转阈值，范围 1 KiB–100 MiB，默认 10 MiB；保留 .1/.2/.3 三个备份。
- redactKeys：追加大小写和分隔符无关的敏感字段关键词，不能取消框架内置规则。

## 输出内容

控制台前缀为 `[TESTKIT CALL]`，其后是单行 JSON。文件保存无前缀 JSONL。
REQUEST 与 RESPONSE/ERROR 使用相同 callId，包含 runId、caseId（当前 TraceContext 可用时）、caseName（当前 CaseContext 可用时）、线程和环境。

- HTTP：目标 URL、方法、脱敏请求头、请求 JSON、响应状态码、脱敏响应头、响应 JSON、耗时。
- Dubbo：直连目标或注册中心服务标记、接口、方法、参数类型、脱敏 arguments/attachments、响应对象、耗时。
- 异常：异常类型链和至多六个代码位置。不复制异常消息，因为消息可能含凭证或原始载荷。

请求先于网络调用记录，超时或连接失败也有 ERROR 记录。日志序列化/磁盘失败不会改变实际调用结果；输出一次安全警告。显式配置的既有 InvocationLogSink 仍保留原本的错误处理语义。

## 隐私与边界

正文先按 JSON/对象结构递归脱敏，再截断。Authorization、Cookie/Set-Cookie、password/token/secret/key、手机号/邮箱/身份证等常见字段被屏蔽；嵌套 JSON 字符串同样处理。URL 查询值及用户信息不输出；非 JSON 文本、二进制和无法序列化的正文不原样打印。
脱敏不能自动识别所有业务秘密：请追加业务专有字段，不把凭证放进 URL 路径、用例名或线程名，敏感环境关闭正文。轮转不是保留策略或访问控制，日志目录仍需项目/CI 权限保护。

一次调用两条日志是正常现象，按 callId 配对；调用成功不等于业务断言通过，断言结果看原 TestNG/JSONL 测试报告。
原 JsonLinesResultPublisher 继续保留无正文的结果及指标，不把详细日志混入默认测试报告；HTTP 默认构造器现在动态路由指标到当前 RuntimeRegistry，修复此前默认 noop 漏接问题。

日志组件不依赖 SLF4J 实现，故旧 SLF4J binder 警告不影响新日志。当前自动接入 HTTP/Dubbo；SDK 可通过公开 start/complete API 适配，未声明 SDK 已自动记录所有调用参数。
