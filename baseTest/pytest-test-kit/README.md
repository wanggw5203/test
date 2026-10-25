# pytest-test-kit

Python/pytest 版通用测试基础库，与 Java 版 [base-test-toolkit](../base-test-toolkit/README.md) 能力一一对应。两套框架共用同一份 YAML 用例套件格式，方便对照学习和双语言作品集展示。

## 快速上手（开箱即用）

环境要求：Python 3.10+（已在 3.14 验证）。

```bash
pip install -r requirements.txt
pytest
```

预期输出：`48 passed`。

入门只需读一个文件：`tests/test_quick_start_demo.py`。
它演示了一条测试的完整生命周期：

```
YAML 数据驱动加载 -> use_identity 身份租借 -> Mock 场景
    -> 业务调用 -> strict 断言（忽略动态字段）
    -> 钩子发布结果 -> 自动清理身份与上下文
```

配套数据在 `tests/cases/`（与 Java 版 `order.yaml` 同一格式）：

| 文件 | 作用 |
|---|---|
| `order.yaml` | 用例套件：2 个启用用例 + 1 个 `enabled: false` 演示过滤 |
| `order-coupon-request.json` | 请求载荷 |
| `order-coupon-response.json` | 期望响应，`orderId` 为动态字段 |
| `order-fullprice-*.json` | 第二条用例的请求与期望 |

## 目录结构

```
pytest-test-kit/
├── conftest.py                    # 生命周期钩子 ↔ PortableTestNgListener
├── pytest.ini                     # pytest 配置
├── requirements.txt
├── pytest_testkit/
│   ├── models.py                  # CaseContext/TestResultRecord 等 ↔ Java record
│   ├── config.py                  # YAML/JSON 加载 ↔ ConfigLoader
│   ├── data_drive.py              # YAML 套件 -> 用例参数 ↔ DataDriveUtil
│   ├── json_diff.py               # 严格/宽松 JSON 比较 ↔ JsonDiff
│   ├── context.py                 # 用例/返回值上下文 ↔ CaseContextHolder + ReturnValueContext
│   ├── identity.py                # 身份上下文 + 账号池 ↔ identity 包
│   ├── mock_engine.py             # Mock 场景/规则 ↔ mock 包
│   └── runtime.py                 # 运行时组合 + 结果发布 ↔ runtime 包
└── tests/
    ├── cases/                     # 演示用 YAML 与 JSON 数据
    ├── test_quick_start_demo.py   # 开箱即用演示（从这里开始）
    ├── test_json_diff.py          # 断言能力单测
    ├── test_data_drive.py         # 数据驱动单测
    ├── test_identity.py           # 身份隔离/并发租借单测
    ├── test_mock_engine.py        # Mock 生命周期单测
    ├── test_runtime.py            # 运行时组合单测
    └── test_http_mock.py          # responses 库 HTTP 打桩示例
```

## 与 Java 版的能力对照

| 能力 | Java（base-test-toolkit） | Python（pytest-test-kit） |
|---|---|---|
| 数据驱动 | `DataDriveUtil` + `@DataProvider` | `load_test_data` + `@pytest.mark.parametrize` |
| 用例上下文 | `CaseContext` + `BaseTest` 绑定 | `CaseContext` + conftest 钩子绑定 |
| JSON 断言 | `JsonDiff.strict/lenient` | `strict/lenient`（语义一致） |
| 身份隔离 | `ThreadLocal` 栈 | `contextvars` 栈 |
| 账号池 | `InMemoryIdentityProvider` | `InMemoryIdentityProvider` |
| 身份声明 | `@UseIdentity("reviewer")` | `@pytest.mark.use_identity("reviewer")` |
| Mock 场景 | `InMemoryMockEngine` | `InMemoryMockEngine` |
| HTTP 打桩 | 接入 WireMock 等 | `responses` 库（见 test_http_mock.py） |
| 生命周期 | `PortableTestNgListener`（ServiceLoader 注册） | 根 `conftest.py` 钩子 |
| 结果发布 | `ResultPublisher` SPI | `ResultPublisher` 协议 |
| 运行时组合 | `AutomationRuntime` + `RuntimeRegistry` | 同名，语义一致 |

## 与 Java 版的已知差异

1. **类型化参数**：Java 用反射把载荷绑定到方法的强类型参数（record）；Python 动态类型，载荷以 `dict` 形式放在 `case.payloads` 里。
2. **JSON5**：Java 的 Jackson 支持注释和尾逗号；Python 标准库只支持标准 JSON，示例数据统一用 `.json`。
3. **数字类型**：Jackson 区分 `IntNode`/`DoubleNode`，Python 中 `180 == 180.0`，`json_diff` 不做整型/浮点区分。
4. **监听器注册**：Java 通过 `META-INF/services` 自动注册；pytest 的惯例是根目录 `conftest.py`，无需注册。

## 引入到自己的项目

把 `pytest_testkit/` 目录复制进项目（或后续发布到私有仓库后 `pip install`），
把根 `conftest.py` 的生命周期钩子合并进项目的 conftest 即可获得：
身份租借、Mock 场景、用例上下文绑定和结果发布。

## 安全边界

- `UserIdentity` 的字符串输出不包含 access token。
- 默认运行时完全离线，不访问网络、不上传结果。
- 真实账号、认证和网络打桩由外部实现显式提供。
