# 接口脚手架生成器规格

## 入口

Skill 自带可执行 JAR：

```text
assets/api-test-scaffold-generator.jar
```

命令：

```bash
java -jar api-test-scaffold-generator.jar help
java -jar api-test-scaffold-generator.jar example
java -jar api-test-scaffold-generator.jar generate --spec SPEC.yaml --project PROJECT
java -jar api-test-scaffold-generator.jar validate --project PROJECT --manifest MANIFEST.yaml
```

`generate` 默认拒绝覆盖。只有确认所有目标文件都可重建时才使用 `--overwrite`。`validate` 默认允许二次加工造成 checksum 变化；加 `--strict-checksums` 时，任何内容变化都会失败。

## 顶层字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `schemaVersion` | 否 | 当前为 `1` |
| `apiCode` | 是 | 稳定接口或场景编号，决定清单目录 |
| `description` | 是 | 中文业务描述 |
| `packageName` | 是 | Java 测试包名 |
| `resourcePackage` | 否 | 资源包名，默认等于 `packageName` |
| `className` | 是 | TestNG 测试类名 |
| `author` | 否 | 默认 `AI` |
| `tester` | 视框架 | 用于 `${tester}` 模板替换 |
| `dataKey` | 是 | `DataDriveUtil` 加载键及 suite 文件名 |
| `template` | 是 | `query` 或 `data-preparation` |
| `environments` | 否 | 默认 `pre`、`test` |
| `java` | 是 | 目标框架 Java 生成参数 |
| `cases` | 是 | 至少一个接口场景 |

## Java 字段

`java.baseClass`、`imports`、`classAnnotations`、`classMembers` 必须从目标仓库黄金测试提取。

以下字段控制 DataProvider 和测试方法：

```yaml
java:
  baseClass: BaseTest
  imports: []
  classAnnotations:
    - '@TestOwner("automation")'
  classMembers: []
  dataProviderName: TestCase
  dataProviderMethod: prepareTestData
  dataLoaderExpression: 'DataDriveUtil.loadTestData(method, "${dataKey}", true)'
  testMethodName: test001
  testParameters:
    - FrameConfigInfo config
    - RequestRO request
    - JsonResponse<ResponseDTO> expect
  setupLines: []
  preparationLines: []
  actualType: JsonResponse<ResponseDTO>
  actualExpression: targetApi.query(request)
  diffExpression: DataDriveUtil.diffFieldValue(expect, actual)
  assertionLines: []
```

`data-preparation` 模板必须包含 `preparationLines`。`assertionLines` 为空时，生成器使用 `diffExpression` 和 `CollectionUtils.isEmpty` 形成默认字段断言；非空时完全采用指定断言。

## 用例与环境覆盖

每条 `cases` 包含：

```yaml
- name: 01_主流程成功
  thought: 来源 STD-API-01，验证提交成功并返回 data=true
  mockId: null
  users: ["1000000"]
  requestRef: 01_主流程成功.json
  expectRef: 01_主流程成功.json
  request:
    base:
      id: __DATA_REF:created_record.id__
    overrides:
      pre: {}
      test: {}
  expect:
    base:
      data: true
    overrides: {}
  fieldHints:
    - field: id
      kind: runtime-business-data
      semantic: 本次运行创建的记录
      valueSource: record.create
      placeholder: __DATA_REF:created_record.id__
      required: true
```

`base` 生成全部环境共有内容；`overrides.<env>` 做递归覆盖。请求和期望会分别生成 `<name>_req_<env>.json5` 与 `<name>_exp_<env>.json5`。

`fieldHints.kind` 推荐取值：

- `fixed`
- `default`
- `business-enum`
- `runtime-business-data`
- `environment-base-data`
- `unique`
- `negative`

## 输出

```text
src/test/java/<package>/<ClassName>.java
src/test/resources/<resourcePackage>/<dataKey>.yaml
src/test/resources/<resourcePackage>/<case>_req_<env>.json5
src/test/resources/<resourcePackage>/<case>_exp_<env>.json5
.api-test-generator/<apiCode>/executableization-plan.yaml
.api-test-generator/<apiCode>/scaffold-manifest.yaml
```

清单记录源规格、所有脚手架文件和 SHA-256。可执行化计划提取每条用例的 `fieldHints`，并明确下一步交给 `make-api-tests-executable`。

## 限制

- 生成器负责确定性渲染，不推断目标仓库 Java 类型和业务枚举。
- 生成器不会连接数据库、服务注册中心或真实接口。
- 二次加工必须由 Skill 结合项目证据完成。
- 生成器不删除旧文件，也不自动修改 Maven 配置。
