# API Test Scaffold Generator

面向 Java/TestNG 数据驱动接口测试项目的独立 CLI/Library。它生成 Java 测试类、`testDataSuite` YAML、多环境 JSON5、脚手架清单和二次加工计划，自身不依赖任何业务测试框架。

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
