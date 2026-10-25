"""数据驱动：YAML 套件 -> 用例参数，对应 Java 的 DataDriveUtil。

Java 版用反射把载荷绑定到测试方法的强类型参数；
Python 动态类型，直接把解析后的载荷列表放进 CaseArguments，
测试里用 case.payloads[0] / case.payloads[1] 取请求和期望。

用法：
    SUITE = load_test_data(Path(__file__).parent / "cases" / "order.yaml")

    @pytest.mark.parametrize("case", SUITE, ids=[c.context.name for c in SUITE])
    def test_create_order(case: CaseArguments):
        request, expected = case.payloads[0], case.payloads[1]
"""

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from pytest_testkit.config import load_config, read_payload
from pytest_testkit.models import CaseContext, TestCaseData, TestDataSuite


@dataclass(frozen=True)
class CaseArguments:
    """一条用例的完整参数：上下文 + 按顺序装载的请求/期望载荷。"""

    context: CaseContext
    payloads: list[Any] = field(default_factory=list)


def load_test_data(suite_file: Path | str) -> list[CaseArguments]:
    """加载 YAML 套件，过滤禁用用例，解析每条用例的载荷文件。

    载荷文件路径相对套件 YAML 所在目录解析（与 Java 行为一致）。
    """
    suite_file = Path(suite_file)
    document = load_config(suite_file)
    suite_node = (document or {}).get("testDataSuite") if isinstance(document, dict) else None
    if not suite_node:
        raise ValueError(f"Missing root node testDataSuite: {suite_file}")
    suite = _parse_suite(suite_node)
    base_directory = suite_file.resolve().parent

    rows: list[CaseArguments] = []
    for case in suite.cases:
        if not case.enabled:
            continue
        if suite.debug_cases and case.name not in suite.debug_cases:
            continue
        payloads = [read_payload(base_directory / name)
                    for name in [*case.request, *case.expect]]
        rows.append(CaseArguments(CaseContext.from_case(case), payloads))
    return rows


def _parse_suite(node: dict) -> TestDataSuite:
    cases = [_parse_case(item) for item in node.get("cases", [])]
    return TestDataSuite(
        description=node.get("description", ""),
        cases=cases,
        debug_cases=list(node.get("debugCases", [])),
    )


def _parse_case(node: dict) -> TestCaseData:
    if not node.get("name"):
        raise ValueError("Case is missing required field: name")
    return TestCaseData(
        name=node["name"],
        enabled=node.get("enabled", True),
        thought=node.get("thought"),
        mock_id=node.get("mockId"),
        users=list(node.get("users", [])),
        request=list(node.get("request", [])),
        expect=list(node.get("expect", [])),
        extra_params=dict(node.get("extraParams", {})),
    )
