# ATC 反馈报告结构

默认必须输出下面的 YAML 结构；Markdown 执行摘要只能作为补充。用户或目标项目明确要求其他机器可读格式时，保留全部等价字段。

## 推荐 YAML

```yaml
feedback:
  runId: ""
  atcFile: ""
  caseName: ""
  functionalCaseIds: []
  status: passed # passed | failed | blocked
  command: ""
  runnerVersion: ""
  environment: ""
  failureStage: ""
  classification: ""
  diagnosticAlias: "" # 可选：其他通用诊断体系的辅助分类
  testedBusinessActionExecuted: unknown # true | false | unknown
  evidence:
    - ""
  repair:
    autoFixable: false
    target: "" # atc | data | environment | yaml | tad | product
    changedFiles: []
    summary: ""
  rerun:
    command: ""
    attempts: 0
    passed: 0
    failed: 0
    result: ""
  goldenPromotion:
    promoted: false
    atcFile: ""
    commitSha: ""
    reason: ""
  upstreamFeedback:
    - target: "" # tad | yaml | atc | data-rule | runner | product
      action: "" # updated | pending | none
      reason: ""
  remainingBlockers: []
```

目标项目已有报告格式时沿用本地格式，但必须保留等价信息。

`classification` 只能是：`ATC_PARSE_OR_SCHEMA_ERROR`、`LOCATOR_OR_PAGE_DRIFT`、`ACTION_SEQUENCE_OR_WAIT_ERROR`、`TEST_DATA_OR_PREREQUISITE_ERROR`、`AUTH_OR_PERMISSION_ERROR`、`ENV_OR_SERVICE_ERROR`、`ASSERTION_OR_EXPECTATION_ERROR`、`PRODUCT_BEHAVIOR_ERROR`、`REQUIREMENT_OR_CASE_CONFLICT`、`UNKNOWN`。其他 Skill 的分类只能写入可选的 `diagnosticAlias`。

## 状态定义

- `passed`：目标用例修复后通过，所有完成条件满足。
- `failed`：用例已执行但仍违反期望，且当前轮次未修复成功。
- `blocked`：环境、权限、外部服务、缺失页面证据或业务决策阻止继续验证。

## 黄金脚本升级条件

同时满足以下条件才设置 `promoted: true`：

- ATC 解析、结构和资源校验通过。
- 目标业务动作确实执行，最终断言与自动化范围一致。
- `LOCATOR_OR_PAGE_DRIFT`、`ACTION_SEQUENCE_OR_WAIT_ERROR` 或有瞬时特征的问题修复后连续两次单例通过；其他确定性修复至少一次干净复跑通过。
- 动态数据可重复生成，上传资源真实存在。
- 无未记录的 TAD、YAML、ATC 和页面证据冲突。
- 记录 ATC 路径、用例名、执行命令、执行器版本、环境和提交 SHA。

项目没有黄金目录时，不复制文件或新增执行器不认识的字段；使用反馈报告记录该 ATC 已成为黄金基线。

## 反馈传播规则

- 只把已确认事实传播到上游或下游。
- 待确认信息保持 `pending`，不能进入可执行期望。
- 修改 TAD 后，重新生成或人工同步受影响的 YAML，再更新 ATC。
- 修改功能 YAML 后，检查全部关联 `functionalCaseIds`。
- 修改黄金 ATC 的共享交互时，扩大回归到复用该交互的用例。
