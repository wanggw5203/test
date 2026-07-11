---
name: api-test-automation-workflow
description: "把 TAD/TRD/STD 生成的接口或服务端 YAML 用例转换为 Put 风格接口自动化脚手架，再对假 ID、枚举、环境基础数据、运行时业务数据和前置状态做二次可执行化加工，完成编译、单例运行、失败诊断、最小修复和上游反馈。用户要求生成接口自动化框架代码、把接口 YAML 转成 Java/TestNG/YAML/JSON5 脚手架、使用 api-test-scaffold-generator.jar、加工 apicode_xxx 用例，或建立接口自动化闭环时使用。"
---

# 接口自动化生成闭环

把上游接口测试意图稳定地落成“框架脚手架 + 可执行数据 + 运行证据 + 上游反馈”。开始前必须阅读 [生成器规格](references/generator-spec.md) 和 [反馈契约](references/api-feedback-contract.md)。

## 确定执行范围

- 只有用户明确要求完整闭环时，才依次执行生成、二次加工、运行诊断和反馈。
- 只要求生成框架时，生成脚手架并完成严格清单校验，不擅自连接真实环境。
- 已有 `apicode_xxx` 脚手架时，不重复生成；直接进入可执行化加工。
- 输入用例的 `tags` 为 `接口` 或 `服务端` 时进入本流程；`前端` 用例交给 `$test-yaml-to-web-atc`。
- 接口契约、鉴权方式或目标框架证据不足时，只生成明确可证实的部分，并记录待确认项。

## 第一阶段：读取框架证据

先检查目标仓库，不凭通用经验虚构类名、包名或调用方式。至少读取：

1. `pom.xml`、父 POM 和测试插件配置。
2. 同模块最近的已跑通 TestNG 测试类、数据驱动 YAML、请求与期望 JSON5。
3. API 封装类、RO/DTO、鉴权注解、`BaseTest`、`DataDriveUtil` 和数据准备器。
4. 仓库内已有生成规则、数据策略、诊断规则和未提交改动。

把已跑通样例视为生成形状的最高优先级证据。工作树有用户改动时必须保留，不得回退或覆盖。

## 第二阶段：形成生成规格

从接口 YAML、TAD/STD 和本地框架证据生成一份 `generation-spec.yaml`。先运行以下命令读取完整示例：

```bash
java -jar <skill-dir>/assets/api-test-scaffold-generator.jar example
```

映射时遵守：

- `caseId/name/thought` 保持上游追溯语义；不得为了适配框架改写业务目标。
- `before` 拆成环境基础数据、运行时造数、鉴权用户和业务前置状态。
- `steps.step` 映射接口调用；`steps.expected` 映射期望 JSON5，不能弱化断言。
- `query` 用于只依赖稳定查询条件的接口；需要先创建、推进或绑定业务状态时使用 `data-preparation`。
- `java.*` 必须来自目标仓库真实类与黄金样例。没有证据时留待确认，不猜测 import、方法签名或响应类型。
- 每个易失字段都写入 `fieldHints`，供二次加工阶段分类处理。

## 第三阶段：生成脚手架

默认禁止覆盖已有文件：

```bash
java -jar <skill-dir>/assets/api-test-scaffold-generator.jar generate \
  --spec <generation-spec.yaml> \
  --project <target-project>
```

生成器输出 Java TestNG 测试类、`testDataSuite` YAML、各环境请求/期望 JSON5、可执行化计划和带 SHA-256 的清单。生成后立即做严格校验：

```bash
java -jar <skill-dir>/assets/api-test-scaffold-generator.jar validate \
  --project <target-project> \
  --manifest <target-project>/.api-test-generator/<apiCode>/scaffold-manifest.yaml \
  --strict-checksums
```

严格校验失败时停止二次加工，先修复规格或生成器问题。除非用户明确要求重建，禁止使用 `--overwrite`。

## 第四阶段：二次可执行化

读取 `executableization-plan.yaml`，并使用 `$make-api-tests-executable` 加工已生成脚手架。框架已生成的业务覆盖默认正确，重点处理数据与运行条件：

- 固定值和明确默认值直接保留。
- 业务枚举只按接口文档、字典、邻近黄金脚本或代码常量转换，不凭字段名猜值。
- 环境基础数据从配置或稳定查询获取。
- 运行时业务数据优先使用 `__DATA_REF:alias.output__`，通过数据准备器创建并回填。
- 唯一字段在运行时生成；反向用例中的非法值、空值和越界值原样保留。
- 前置数据失败应阻塞或跳过目标调用，不把造数失败伪装成接口断言失败。

业务造数放在 databuilder 或项目已有数据准备层；测试类只保留编排、调用和断言。禁止删除用例、缩小覆盖或把具体期望改成宽松成功判断。

二次加工后使用宽松清单校验：

```bash
java -jar <skill-dir>/assets/api-test-scaffold-generator.jar validate \
  --project <target-project> \
  --manifest <manifest.yaml>
```

`Modified` 是预期的加工证据；`Missing` 和 `Malformed` 必须为 0。

## 第五阶段：编译与运行

先按目标仓库既有命令做测试编译，再运行最小目标单例。优先顺序：

1. `mvn -DskipTests test-compile`
2. `mvn -Dtest=<GeneratedTests> test`
3. 仓库已有的环境参数、TestNG suite 或启动脚本

不要一开始运行全量测试。真实环境需要账号、网络或数据权限时，明确记录阻塞证据，不伪造通过结果。

## 第六阶段：诊断修复

编译或运行失败时使用 `$diagnose-and-repair-tests`：

1. 先按编译、启动、鉴权、网络、数据准备、业务响应、字段对比分类。
2. 找到第一条决定性证据，再做最小修复。
3. 每次只修一个根因并复跑同一单例。
4. 产品缺陷和真实契约不符不得通过修改期望来“修绿”。
5. 环境问题不得写成业务规则。

## 第七阶段：回写闭环

根据 [反馈契约](references/api-feedback-contract.md) 生成 `<apiCode>.api.feedback.yaml`，并按责任边界回写：

- 框架模板、路径或渲染错误：修生成规格或生成器。
- 易失数据、枚举或造数错误：修 `fieldHints`、数据策略或 databuilder。
- 业务规则冲突：确认后按 TAD/STD -> 接口 YAML -> 脚手架传播。
- 产品缺陷、环境或权限问题：保留证据并标记阻塞，不改测试期望。
- 稳定跑通的单例：登记为接口黄金样例，供后续生成规格校准。

始终维护：

```text
需求规则 -> TAD/STD -> 接口 YAML caseId -> generation-spec
-> scaffold-manifest -> Java/YAML/JSON5 -> runId -> feedback/golden sample
```

## 完成门禁

- 生成规格可解析，且所有 Java 框架元素都有本地证据。
- 首次严格清单校验通过。
- 二次加工后无缺失或畸形产物，反向场景未被破坏。
- 测试编译通过；有环境条件时目标单例运行通过。
- 未通过项有明确根因、证据、责任边界和下一步。
- 上游反馈与黄金样例登记完成，且没有静默覆盖用户文件。

## 汇报结果

说明生成规格、Java/YAML/JSON5、清单、可执行化计划和反馈文件的位置；列出执行命令、通过项、阻塞项、二次加工的数据类型，以及是否形成黄金样例。不得只汇报“已生成”而省略运行证据。
