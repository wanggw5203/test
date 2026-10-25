# API Test Scaffold Generator

面向 Java/TestNG 数据驱动接口测试项目的独立 CLI/Library。它生成 Java 测试类、`testDataSuite` YAML、多环境 JSON5、脚手架清单和二次加工计划。

- v1 `generic`：保留原来的框架中立模板。
- v2 `basetest-http`：生成直接使用 `base-test-toolkit` `ApiClient` 的 TestNG 测试。
- v2 `basetest-dubbo`：生成直接使用 `enterprise-adapters` `DubboClient` 的 TestNG 测试。

v2 会把 `sourceCaseIds`、接口选择器、协议、BO/VO/DTO 类型和源码证据写入生成清单，使“测试用例 → 接口契约 → 自动化代码”可以通过 Git 追溯。

## 构建

```bash
mvn clean package
```

产物：`target/api-test-scaffold-generator.jar`。

## 使用

```bash
java -jar target/api-test-scaffold-generator.jar example > generation-spec.yaml
java -jar target/api-test-scaffold-generator.jar generate \
  --spec generation-spec.yaml \
  --project /path/to/http-test-project
```

生成后会在目标项目创建：

- `src/test/java/.../*Tests.java`
- `src/test/resources/.../*.yaml`
- `src/test/resources/.../*_req_pre.json5` 与 `*_req_test.json5`
- `src/test/resources/.../*_exp_pre.json5` 与 `*_exp_test.json5`
- `.api-test-generator/<apiCode>/scaffold-manifest.yaml`
- `.api-test-generator/<apiCode>/executableization-plan.yaml`

默认拒绝覆盖已有文件。明确需要重新生成时增加 `--overwrite`。

## 校验

```bash
java -jar target/api-test-scaffold-generator.jar validate \
  --project /path/to/http-test-project \
  --manifest /path/to/http-test-project/.api-test-generator/<apiCode>/scaffold-manifest.yaml
```

二次加工会正常修改生成资产，因此默认只把文件缺失和结构错误作为失败；增加 `--strict-checksums` 可同时要求内容仍与原始脚手架完全一致。

## v2 接口契约

HTTP 最小配置：

```yaml
schemaVersion: 2
sourceCaseIds: [TC-HTTP-001]
target: { protocol: HTTP }
endpoint: { method: POST, path: /api/orders }
generator: { profile: basetest-http }
```

Dubbo 最小配置：

```yaml
schemaVersion: 2
sourceCaseIds: [TC-DUBBO-001]
target: { protocol: DUBBO }
endpoint:
  interfaceName: com.example.OrderService
  dubboMethod: create
  parameterTypes: [com.example.CreateOrderBO]
generator: { profile: basetest-dubbo }
```

以上片段还需要配合 v1 已有的 `apiCode`、类名、包名、`dataKey`、环境和 `cases`。完整示例见：

- `src/main/resources/example-http-v2-spec.yaml`
- `src/main/resources/example-dubbo-v2-spec.yaml`

生成的 HTTP 测试从 `api.baseUrl` 系统属性读取地址；Dubbo 测试从 `dubbo.registry` 或 `dubbo.directUrl` 系统属性读取连接信息。凭据和环境地址不会写入规格或生成代码。
