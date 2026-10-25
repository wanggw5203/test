# 本地 HTTP 与真实 Dubbo 完整流程复验

验收日期：2026-10-18，Asia/Shanghai。首次完整验证约 10:15 完成，一键脚本复验约 10:18 完成。

## 结论

**本地服务设计、启动、双协议资产生成、跨进程调用、断言、故障检测和恢复复跑均已通过，并完成第二轮脚本复验。**

“真实 Dubbo”指独立服务进程通过 Dubbo TCP 对外发布类型明确的 Java 接口，自动化在另一 JVM 使用框架 DubboClient 泛化调用；不是 HTTP 模拟 RPC，也不是测试直接调用实现类。

默认模型仍为黄金集，不能据此宣称远程 AI 分析和模型反馈已验收。该限制不影响此次 HTTP/Dubbo 协议的真实执行结论。

## 服务与接口

| 内容 | 配置 |
| --- | --- |
| HTTP 接口 | POST http://127.0.0.1:18081/demo/orders |
| 健康检查 | GET http://127.0.0.1:18081/health |
| Dubbo 直连地址 | dubbo://[::1]:20891 |
| Dubbo 接口与方法 | demo.golden.OrderService#create(java.util.Map) |
| 注册中心 | 不需要，Registry=N/A |
| 故障控制 | POST /admin/fault，JSON enabled=true/false |

三个场景：合法商品和数量创建成功；quantity=0 拒绝；未传 sku 拒绝。HTTP 与 Dubbo 共享 OrderRules，仅传输适配不同。
实际核对监听端口：HTTP 绑定 127.0.0.1，Dubbo 绑定 ::1，没有额外网络元数据服务端口；服务保留运行，故障开关已恢复为 false。

## 本轮从需求开始执行的流程

1. 在 requirements 中维护新需求，包含规则、HTTP URL、RPC 签名和故障验收要求。
2. 用独立初始化器分别创建 http-tests、dubbo-tests，自动引入框架依赖、建立 main/test、启动与环境配置。
3. 两个项目分别使用插件同源 SkillRepository、LlmClient、AssetPlan 和质量门禁，生成 TAD、YAML 功能用例、协议对应接口规格。
4. 使用实际 ScaffoldGenerationService 核对上游 caseId，生成 main 的 API/DataBuilder、test 的 BaseTest 测试方法、套件 YAML 与六个 JSON5 数据文件。
5. 启动独立服务提供者，在两个测试工程中实际执行 Maven/TestNG。
6. 打开故障开关，重跑两个工程；再关闭故障开关并重跑。
7. 保存 Maven 日志、框架 JSONL、TestNG XML、服务端审计、原始模型反馈和验收程序派生的执行反馈。
8. 通过 Verify-FullFlow.ps1 再完整执行一轮，验证生成复用、脚本入口和调用链可重复运行。

## 每轮实际结果

| 协议 | 正常基线 | 故障注入 | 恢复复跑 |
| --- | --- | --- | --- |
| HTTP | 4 通过，0 失败 | 3 通过，1 失败，准确命中 GOLD-002 | 4 通过，0 失败 |
| Dubbo | 4 通过，0 失败 | 3 通过，1 失败，准确命中 GOLD-002 | 4 通过，0 失败 |

每个协议的 4 条包含 3 条业务场景和 1 条工程初始化验证。每轮六次执行，共 24 次测试调用；故障轮次的两条失败是预期检测结果，不是被隐藏或改成成功。
服务端审计确实记录 HTTP 与 DUBBO 两种协议及 BUG_ZERO_ACCEPTED 故障响应，原始证据可交叉核对。

## 原始证据

最新脚本复验：

- [双协议完整报告](quality-assets/evidence/run-1792289867182/full-flow-report.json)
- [服务端调用审计快照](quality-assets/evidence/run-1792289867182/provider-audit.jsonl)
- [HTTP 恢复结果](http-tests/quality-assets/evidence/run-1792289867182/recovered-results.jsonl)
- [Dubbo 恢复结果](dubbo-tests/quality-assets/evidence/run-1792289867182/recovered-results.jsonl)
- [Dubbo 故障结果](dubbo-tests/quality-assets/evidence/run-1792289867182/fault-injection-results.jsonl)
- [HTTP 派生执行反馈](http-tests/quality-assets/evidence/run-1792289867182/execution-feedback.yaml)
- [Dubbo 派生执行反馈](dubbo-tests/quality-assets/evidence/run-1792289867182/execution-feedback.yaml)

首次验证保存在 quality-assets/evidence/run-1792289725772，两个测试工程也有同名轮次的各自证据。

## 明确没有判定通过的部分

- 默认 order-create-v1 黄金集是固定响应；PRD/TAD/用例工程链路已走通，但任意新需求的真实模型语义分析未验证。
- 黄金集反馈在收到真实日志后仍返回 blocked/not-run。原始文本保留在 model-feedback-after-execution.yaml，不能作为此次真实执行状态。execution-feedback.yaml 来自验收程序读取 JSONL 的确定性汇总，明确不是插件模型自动反馈闭环。
- 本次未在 IDEA 界面逐个点击和保存文档；服务层验收不冒充 GUI/VFS 回归。
- 未验收注册中心、数据库、真实造数平台、页面执行或业务 SDK，也不代表公司后端已通过测试。
- 接口证据来自明确输入与本地源码，未把它描述为插件已自动发现完整调用链。

## 后续直接使用

查看 [README](README.md) 中的调用和脚本说明。服务当前保持运行，可直接用上述地址验证；不用时运行 Stop-LocalService.ps1，仅停止核对过归属的本地服务进程，不删除工程文件。
