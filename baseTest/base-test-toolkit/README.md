# Base Test Toolkit

这是从现有测试基础库中抽取并重新实现的中性 Java 测试能力。它只使用公开 Maven 依赖，不依赖企业认证、配置中心、服务治理、Mock 平台、覆盖率平台或专用测试平台。

## 保留能力

- 从 YAML、JSON、JSON5 加载强类型配置和测试数据。
- 使用 `testDataSuite`、`cases`、`request`、`expect` 组织数据驱动用例。
- 按测试方法参数类型把请求和预期文件反序列化为 Java 对象。
- 提供严格 JSON 比较、宽松 JSON 比较和忽略路径。
- 提供线程隔离的用例上下文和返回值上下文。
- 提供轻量 TestNG `BaseTest`、测试人员标记和分页模型。
- 从系统属性和环境变量解析测试环境。

## 构建

要求 JDK 17 及 Maven 3.9+：

```bash
./build.sh
```

输出文件：`target/base-test-toolkit-1.0.0.jar`。

## 数据驱动示例

```java
@DataProvider(name = "cases")
public Object[][] cases(Method method) {
    return DataDriveUtil.loadTestData(method, "search");
}

@Test(dataProvider = "cases")
public void search(CaseContext context, SearchRequest request, SearchResponse expected) {
    SearchResponse actual = client.search(request);
    Assert.assertTrue(DataDriveUtil.diffFieldValue(expected, actual).isEmpty());
}
```

默认从测试类包路径寻找 `search.yaml`，请求和预期文件相对 YAML 所在目录解析。也可以直接调用 `loadTestData(method, suitePath)`。

## 配置覆盖

- `-Dtest.env=integration` 或环境变量 `TEST_ENV=integration`。
- `-Dtest.attr.region=east` 或环境变量 `TEST_ATTR_REGION=east`。
- 系统属性优先于环境变量。
