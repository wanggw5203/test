# Testkit 企业自动化框架

用于 Java/TestNG 分层自动化测试：HTTP、Dubbo、SDK、XXL-JOB/本地任务、消息消费与数据库验证；支持 local/dev/qa、多团队归属、调用隔离、稳定分片、资源租约及 CI 完整性门禁。当前开发版本为 `1.1.0-SNAPSHOT`，Java 编译基线为17。

## 从这里开始

框架学习统一阅读 [Testkit自动化框架完整学习手册](Testkit自动化框架完整学习手册.md)：分层能力、执行流程、构建接入、源码解析、逐步调试、八个实验、平台扩展与故障排查均已合并，不再要求切换多份学习资料。

| 目的 | 入口 |
|---|---|
| 看整个执行闭环和本轮检查结论 | [闭环审计](docs/LIFECYCLE-AUDIT.md) |
| 查模块、类、用例、配置及资源命名 | [工程规范与兼容迁移](docs/ENGINEERING-STANDARDS.md) |
| 查协议能力及实际验证范围 | [能力总览](ENTERPRISE-CAPABILITIES.md) |
| 组织约100人的多团队接入 | [企业团队模式](ENTERPRISE-TEAM-MODE.md) |
| 运行新团队接入示例 | [OrderWorkflowTest 示例](enterprise-team-example/README.md) |
| 部署本地鉴权服务并动手学习 | [订单演练](java-api-lab/README.md)、[源码学习路线](base-test-toolkit/LEARNING-GUIDE.md) |
| 查前沿技术补齐优先级和验收条件 | [技术路线图](docs/TECHNOLOGY-ROADMAP.md) |
| 查维护依赖调整及安全扫描范围 | [依赖审查](docs/DEPENDENCY-REVIEW.md) |

## 完整验证

需要 Maven3.9、受支持的JDK17以上版本及PowerShell7.2以上。在本目录执行：

```powershell
./verify-lifecycle.ps1
```

统一入口固定本地、全量、单分片执行，不访问公司Kafka/XXL-JOB，依次执行：

```text
前置检查与源码指纹
  → Maven clean verify：编译、TestNG、统一格式检查
  → CycloneDX SBOM：包括测试作用域依赖
  → 四个测试模块报告门禁
  → 企业示例计划/JSONL/summary完整性门禁
  → 报告、分片、证据门禁拒绝路径回归
  → 源码未变化检查及制品/报告/计划摘要
  → 复验证据，形成 PASSED 或 FAILED 结果
```

完整成功后的主要产物为 `target/verification-summary.json`（实际测试数量）、`target/verification-evidence.json`（runId、源码指纹、工具版本、各项检查及制品SHA256）、`target/testkit-sbom.json/xml`（CycloneDX1.6依赖清单），以及各模块的Surefire XML、JAR和企业示例JSONL/计划。

遗留 `verify-framework.ps1` 仍用于构建和模块报告检查；完整生命周期验收使用新入口。CI配置见 [CI接入](ci/README.md)。生成目录会被下一次clean覆盖，应归档本次原始产物。证据索引与哈希校验不等于数字签名、漏洞扫描或SLSA认证。

通过完整验证后，可执行 `mvn -B -ntp install -DskipTests` 将刚验证的构件安装到本机。业务项目通过父POM/BOM消费批准版本；私有仓库发布、扫描及审批按公司流程单独实施。

## 开发与边界

修改Java后运行 `mvn spotless:apply`，提交前运行完整验证。已有Maven坐标、包名、公开配置键和稳定用例ID保持兼容；新用例使用 `@TestCaseMetadata`，旧 `@CaseInfo` 和 `XxlJobDialect.legacy()` 有兼容代理及弃用说明。

本地回归、协议模拟与真实平台验收分别记录。Kafka/XXL-JOB live profile会实际发送消息或触发专用任务，只在批准的独立dev/qa任务中启用。企业权限、真实容器/中间件、生产同型数据库租约、多节点故障及长时间容量仍需环境证据，详见闭环审计和技术路线图。
