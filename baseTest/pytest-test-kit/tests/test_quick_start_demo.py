"""开箱即用的快速上手示例，对应 Java 的 QuickStartDemoTest。

一个文件演示一条测试的完整生命周期：
YAML 数据驱动加载 -> use_identity 身份租借 -> Mock 场景
    -> 业务调用 -> strict 断言（忽略动态字段）-> 结果发布 -> 自动清理

运行方式：pytest tests/test_quick_start_demo.py -v
配套数据：tests/cases/order.yaml（与 Java 版共用同一套件格式）
"""

import uuid
from pathlib import Path

import pytest

from pytest_testkit import (
    AutomationRuntime,
    CaseArguments,
    CaseContextHolder,
    IdentityContext,
    InMemoryIdentityProvider,
    InMemoryResultPublisher,
    ReturnValueContext,
    RuntimeRegistry,
    Status,
    UserIdentity,
    lenient,
    load_test_data,
    strict,
)

SUITE = load_test_data(Path(__file__).parent / "cases" / "order.yaml")
RESULTS = InMemoryResultPublisher()


def _create_order(request: dict) -> dict:
    """真实项目中这里发送 HTTP 请求；示例用本地模拟代替，保证离线可运行。"""
    total = request["quantity"] * 100
    if request.get("couponCode") == "SAVE20":
        total -= 20
    order_id = f"ORD-{uuid.uuid4()}"
    # 造数阶段的中间结果可以写入 ReturnValueContext，供后续步骤读取。
    ReturnValueContext.put("orderId", order_id)
    return {"code": 0, "orderId": order_id, "totalPrice": total}


@pytest.fixture(scope="module", autouse=True)
def install_demo_runtime():
    """模块级安装运行时，对应 Java 的 @BeforeClass installDemoRuntime。"""
    accounts = {
        "reviewer": [
            UserIdentity("reviewer-1", "u-1001", "token-r1", {"role": "reviewer"}),
            UserIdentity("reviewer-2", "u-1002", "token-r2", {"role": "reviewer"}),
        ],
        "operator": [
            UserIdentity("operator-1", "u-2001", "token-o1", {"role": "operator"}),
        ],
    }
    RuntimeRegistry.install(AutomationRuntime(
        identities=InMemoryIdentityProvider(accounts), results=RESULTS))
    yield
    RuntimeRegistry.reset()


@pytest.mark.parametrize("case", SUITE, ids=[row.context.name for row in SUITE])
@pytest.mark.use_identity("reviewer")
def test_create_order(case: CaseArguments):
    request, expected = case.payloads

    # 生命周期钩子已把 CaseContext 绑定到当前上下文。
    assert CaseContextHolder.require_current().name == case.context.name

    # use_identity 标记触发账号租借，这里直接读取当前身份。
    reviewer = IdentityContext.require_current()
    assert reviewer.attributes["role"] == "reviewer"

    actual = _create_order(request)

    # orderId 是动态字段，用忽略路径跳过；其余字段严格比较。
    differences = strict(expected, actual, "$.orderId")
    assert differences == [], f"Response mismatch: {differences}"

    # 宽松模式额外容忍实际结果多出的字段。
    assert lenient(expected, {**actual, "traceId": "t-1"}, "$.orderId") == []


def test_lifecycle_is_managed_and_resources_are_released():
    records = RESULTS.results
    assert len(records) >= 2, "每条数据驱动用例都应发布一条结果"
    assert all(record.status == Status.PASSED for record in records)

    # 用例结束后身份已归还、上下文已清理。
    assert IdentityContext.current() is None
    assert ReturnValueContext.get("orderId") is None

    # 身份归还后可以立即被下一个用例租用。
    runtime = RuntimeRegistry.current()
    identity = runtime.identities.acquire("reviewer")
    runtime.identities.release(identity)
