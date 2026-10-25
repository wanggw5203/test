# 从独立需求项目开始的整套流程验收

验收时间：2026-10-18 09:43–09:44，Asia/Shanghai。

结论：**HTTP 资产生成、框架执行与故障检测已验证；当前默认黄金集模式下，不能判定自动反馈闭环完成。**

## 本次新建的项目

项目名 requirement-order-flow，通过独立初始化器实际生成，不复用旧 demo 的 POM 或代码。原始需求在 [order-create.prd.md](quality-assets/inputs/order-create.prd.md)。

需求为“订单创建与参数校验”，包含成功创建、数量为零、未传商品编码三条规则。前三条规则刻意匹配当前默认黄金集，另加 BR-004 变化探针检查固定响应的边界。

使用插件相同的内置 Skill 加载器、默认 LlmClient、AssetPlan、质量门禁和 ScaffoldGenerationService；通过文件系统验收程序保存产物，不通过 IDEA GUI/VFS 点击操作。默认模型配置没有发送远程请求，因此没有验证真实大模型的分析质量。

## 分阶段结果

| 阶段 | 实际结果 |
| --- | --- |
| HTTP 工程初始化 | 成功生成框架依赖、main/test、bootstrap、环境加载、报告和初始化验证用例 |
| PRD → TAD | 默认黄金集输出落入 quality-assets/analysis，结构门禁通过 |
| TAD → 用例 | 生成 GOLD-001/002/003，追溯 BR-001/002/003，门禁通过 |
| 用例 → 接口规格 | 核对 POST /demo/orders、HTTP Profile 和上游 caseId，通过 |
| 接口规格 → 框架资产 | 生成 14 个文件：main API/DataBuilder，test 测试类、YAML、6 个 JSON5，以及清单等 |
| 实际编译及运行 | main/test 编译通过；3 条 HTTP 场景 + 1 条初始化验证全部通过 |
| 故障注入 | 将数量为零的状态码从 400 故意改为 200，GOLD-002 断言失败，其他 3 条通过 |
| 恢复服务后复跑 | 恢复 400 后重新 4 条通过 |
| 生成保护 | 重复生成可复用；人工修改代码时阻止覆盖；错误上游 caseId 被拒绝 |
| Web ATC | 只生成待页面证据资产；没有页面，不声称执行通过 |
| 执行反馈 | 黄金集在提供真实执行日志后仍返回 blocked/not-run，未依据证据更新 |

本地 HTTP 服务由独立的验收服务逻辑实现，不从生成的期望文件中直接读取响应。三轮共捕获 9 次 HTTP 请求。它验证测试真正发请求并做断言，不代表公司业务后端已经通过测试。

## 两个已确认的限制

1. 新增“BR-004：负数数量必须拒绝”后，黄金集仍输出原来的三条用例，内容完全相同。因此“门禁通过”不等于“新需求分析正确”。需要接入真实模型后单独验收语义覆盖。
2. 将实际 TestNG 执行结果传给反馈阶段后，默认黄金集反馈仍是“未执行”。这不是本次接口测试没运行，而是固定演练响应没有消费执行证据；正常模型模式的结果分析本次未验证。不能因此把整套流程标成自动闭环全部完成。

另外，本次未做新版 IDEA 界面/文档保存路径手工回归，未调用真实 Dubbo、SDK、数据库、注册中心或业务造数能力。此次业务 DataBuilder 是扩展点，不能宣称已经接入真实造数平台。

## 原始证据

- [机器可读验收结果](quality-assets/evidence/validation-report.json)：逐项状态及生成文件路径。
- [首次运行日志](quality-assets/evidence/baseline-maven.log)：4 条通过。
- [故障注入结果](quality-assets/evidence/fault-injection-results.jsonl)：GOLD-002 FAILED。
- [恢复后的运行日志](quality-assets/evidence/recovered-maven.log)：4 条重新通过。
- [恢复后的框架结果](quality-assets/evidence/recovered-results.jsonl)：包含三个业务 caseId 与初始化结果。
- [实际 HTTP 请求](quality-assets/evidence/http-requests.json)：三轮请求与响应状态记录。
- [执行后再次生成的固定反馈](quality-assets/evidence/feedback-after-real-execution.yaml)：仍为 blocked/not-run。
- [.ai-test-flow/manifests/validation-inventory.json](.ai-test-flow/manifests/validation-inventory.json)：生成验收阶段的文件路径与 SHA-256，作用域明确为验收程序，不冒充 GUI TraceabilityService 记录。

所有输入、代码、用例、数据与原始证据均保留在该项目目录，可由当前仓库 Git 跟踪。未新建嵌套 Git 仓库，未自动提交或推送，也未修改原有业务项目。

## 在该项目中复跑

为方便单独运行，提供与验收契约等价的 Node 本地服务。原始证据来自 Java 验收程序的 HttpServer，而非此复跑脚本。

终端 1，在项目目录执行：

```powershell
node tools/order-fixture.mjs --port 18880
```

终端 2，在同一目录执行（JDK 21、框架依赖已可从 Maven 仓库解析）：

```powershell
mvn test "-Dapi.baseUrl=http://127.0.0.1:18880/" "-Dtestkit.report.file=quality-assets/evidence/replay-results.jsonl"
```

若要复现失败，在终端 1 停止本地服务，再使用 `--faulty-zero-status` 参数启动；此时重跑应出现 1 条失败。报告文件按框架规则追加写入，请选择新文件名保留独立轮次。不要使用原始 baseline/fault-injection/recovered 文件名。

整个生成与执行验收程序位于仓库的 tools/ai-test-flow-idea-plugin/src/test/java/io/github/aitestflow/service/RequirementProjectE2EValidationTest.java。
重新跑完整创建流程时必须选择新目录；程序故意拒绝覆盖已经初始化的项目。平时的插件单元测试只在临时目录检查资产生成，不会启动额外 Maven/服务；显式设置 AI_TEST_FLOW_VALIDATION_ROOT 才运行完整验收。
