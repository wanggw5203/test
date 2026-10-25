# 本地 HTTP + Dubbo 真协议验收项目

首次手动操作请先阅读 [完整使用手册](完整使用手册.md)，包含服务启动、Dubbo 配置、插件逐步生成、测试执行、日志排查与 Git 管理。实际复验记录见 [本轮复验结果](本轮复验结果.md)。

该项目设计并运行一套简单订单服务，提供 HTTP URL 和真正的 Dubbo RPC。服务与自动化测试在不同 JVM 中，无公司环境、数据库或注册中心依赖。

## 服务地址

- HTTP：`POST http://127.0.0.1:18081/demo/orders`。
- 健康检查：`GET http://127.0.0.1:18081/health`。
- Dubbo：`dubbo://[::1]:20891`，接口 `demo.golden.OrderService`，方法 `create(java.util.Map)`，返回 Map；无 group/version。
- 故障注入：`POST http://127.0.0.1:18081/admin/fault`，JSON `{"enabled":true}`，恢复使用 false。

HTTP 只绑定 IPv4 回环。Dubbo 3.3.5 会将 127.* 当成自动探测地址，因此这里显式使用 IPv6 回环 `::1`，并将元数据服务限制为 injvm，避免额外开启局域网监听端口。Dubbo 地址不能用浏览器当 HTTP 页面打开。

## 简单调用

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18081/demo/orders -ContentType application/json -Body '{"sku":"SKU-DEMO","quantity":1}'
```

返回 `code=OK` 和 `orderId=ORDER-DEMO-001`。quantity=0 返回 INVALID_QUANTITY，未传 sku 返回 SKU_REQUIRED。

## 项目内容

- requirements/order-service.prd.md：独立需求与双协议契约。
- service：单独启动的真实服务提供者；OrderRules 共享校验，HTTP 与 Dubbo 只负责传输适配。
- http-tests、dubbo-tests：分别由初始化器建立，随后通过插件同源生成链路写入 TAD、用例、接口规格、main API/DataBuilder、TestNG 类、YAML 和 JSON5。
- quality-assets/evidence：跨协议验收总报告和服务端审计快照。
- 各测试项目 quality-assets/evidence：逐轮 Maven 日志、框架 JSONL、TestNG XML、派生执行反馈。

## 启动、停止和完整复验

Windows 最简单的方式：双击本目录的 `start-service.cmd`。它会自动构建并后台启动 HTTP 与 Dubbo；双击 `stop-service.cmd` 停止脚本启动的实例。不要同时在 IDEA 中运行 LocalOrderApplication，否则会端口冲突；IDEA 启动的实例请在 IDEA 中停止。脚本只对当前进程绕过执行策略，不修改系统策略。

需要 JDK 21、Maven 和已可解析的框架 1.1.0-SNAPSHOT。

```powershell
.\Start-LocalService.ps1
.\Verify-FullFlow.ps1 -Fresh -ConfigOnly
.\Stop-LocalService.ps1
```

启动脚本后台隐藏运行服务，不会弹出额外控制台；停止脚本先核对 PID 对应的完整 JAR 路径，不误停其他 Java 进程。
完整复验保留原有生成资产，不覆盖人工修改，重新记录独立轮次。它依次运行 HTTP/Dubbo 基线、故障注入、恢复复跑，并保证最终关闭故障开关。复验包含真实网络调用，但不是 IDEA GUI 点击回归。

仅运行现有 Dubbo 自动化工程（它包含泛化调用示例）：

```powershell
mvn -f dubbo-tests/pom.xml test "-Ddubbo.directUrl=dubbo://[::1]:20891" "-Ddubbo.registry=N/A"
```

仅运行 HTTP：

```powershell
mvn -f http-tests/pom.xml test "-Dapi.baseUrl=http://127.0.0.1:18081/"
```

## 本轮通过的范围和限制

两种协议各有 3 条业务测试和 1 条初始化验证。基线各 4 条通过；破坏数量为零规则后各 1 条业务测试失败；恢复后各 4 条通过。

需求/TAD/用例生成仍使用默认 order-create-v1 黄金集，而不是远程模型。此处真实服务按该已声明的契约实现，不宣称验证了任意需求的 AI 分析质量或自动源码发现能力。
默认模型反馈仍返回固定 blocked/not-run；基于实际 JSONL 汇总的 execution-feedback.yaml 明确标注为验收程序派生，不冒充插件模型自动反馈。原始模型反馈也同时保留。
没有页面、业务造数平台、生产数据或真实 SDK，相关能力不列入通过范围。

所有源代码、需求、生成物和证据位于当前 Git 仓库；未自动提交或推送。runtime 是运行状态，service/target 和各项目 target 是构建输出，不应当成源码版本管理。
