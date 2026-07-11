# 接口自动化反馈契约

## 默认文件

将运行结论写入：

```text
.api-test-generator/<apiCode>/<apiCode>.api.feedback.yaml
```

推荐结构：

```yaml
feedback:
  apiCode: apicode_xxx
  sourceCaseIds: [API-001]
  manifest: .api-test-generator/apicode_xxx/scaffold-manifest.yaml
  runId: "20260710-001"
  environment: pre
  command: mvn -Dtest=ExampleTests test
  result: passed
  stage: assertion
  rootCause: none
  evidence:
    - target/surefire-reports/ExampleTests.txt
  changes:
    - type: data-preparation
      file: src/test/java/.../ExampleDataPreparer.java
      summary: 运行时创建业务记录并回填 id
  upstreamFeedback: []
  goldenSample:
    eligible: true
    reason: 连续运行通过且无固定易失业务 ID
```

## result

- `passed`：目标接口被真实调用，业务断言通过。
- `failed`：接口已调用，响应或断言暴露真实失败。
- `blocked`：编译、环境、网络、鉴权、依赖或前置数据阻止了有效调用。
- `skipped`：因已识别的前置条件不满足而明确跳过。

不得把 `blocked` 或 `skipped` 写成 `passed`。

## stage

取最早决定结果的阶段：

- `generation`
- `manifest-validation`
- `compile`
- `startup`
- `authentication`
- `network`
- `data-preparation`
- `request`
- `business-response`
- `assertion`
- `cleanup`

## 根因与回写位置

| 根因 | 首选回写位置 |
| --- | --- |
| 生成路径、模板或渲染错误 | `generation-spec.yaml` 或生成器源码 |
| 假 ID、环境数据、唯一字段 | `fieldHints`、JSON5、数据准备器 |
| 枚举转换错误 | 接口字典证据、`_enum`、生成规格 |
| 鉴权或账号权限 | 环境配置、账号准备说明 |
| 真实接口契约变化 | STD、接口 YAML、RO/DTO，经确认后传播 |
| 产品缺陷 | 缺陷记录和执行证据，不修改期望 |
| 框架基础设施失败 | Maven/Put 配置或环境阻塞记录 |

## 黄金样例门禁

只有同时满足以下条件才登记 `eligible: true`：

- 目标单例在真实环境调用并通过。
- 不依赖固定易失业务 ID。
- 请求、期望、用户和前置数据来源可追溯。
- 断言未被放宽。
- 至少复跑一次仍通过，或项目已有等价稳定性证据。

黄金样例用于校准后续的包结构、DataProvider、数据准备和断言形状，不用于复制过期业务数据。
