"""严格/宽松 JSON 比较，对应 Java 的 JsonDiff。

语义与 Java 版保持一致：
- strict：期望中的每个字段都必须存在且相等，实际结果多出的字段报 UNEXPECTED。
- lenient：允许实际结果多出字段，只检查期望覆盖的部分。
- 忽略路径支持通配符，如 "$.*.id"，"*" 不跨越 "." 和 "[]"。

与 Java 版的一个已知差异：Jackson 区分 IntNode 和 DoubleNode，
而 Python 中 180 == 180.0，数字不做整型/浮点类型区分。
"""

import re
from typing import Any

from pytest_testkit.models import Difference, DifferenceType

_MISSING = "<missing>"


def strict(expected: Any, actual: Any, *ignored_paths: str) -> list[Difference]:
    return _compare(expected, actual, allow_extra_actual=False, ignored=set(ignored_paths))


def lenient(expected: Any, actual: Any, *ignored_paths: str) -> list[Difference]:
    return _compare(expected, actual, allow_extra_actual=True, ignored=set(ignored_paths))


def _compare(expected: Any, actual: Any, allow_extra_actual: bool,
             ignored: set[str]) -> list[Difference]:
    differences: list[Difference] = []
    _compare_node(expected, actual, "$", allow_extra_actual, ignored, differences)
    return differences


def _compare_node(expected: Any, actual: Any, path: str, allow_extra_actual: bool,
                  ignored: set[str], differences: list[Difference]) -> None:
    if _is_ignored(path, ignored):
        return
    expected_kind = _kind(expected)
    actual_kind = _kind(actual)
    if expected_kind != actual_kind:
        differences.append(Difference(DifferenceType.TYPE, path, expected_kind, actual_kind))
        return
    if expected_kind == "object":
        for key, value in expected.items():
            child_path = f"{path}.{key}"
            if key not in actual:
                differences.append(
                    Difference(DifferenceType.MISSING, child_path, _text(value), _MISSING))
                continue
            _compare_node(value, actual[key], child_path, allow_extra_actual, ignored, differences)
        if not allow_extra_actual:
            for key, value in actual.items():
                if key not in expected:
                    child_path = f"{path}.{key}"
                    if not _is_ignored(child_path, ignored):
                        differences.append(Difference(
                            DifferenceType.UNEXPECTED, child_path, _MISSING, _text(value)))
        return
    if expected_kind == "array":
        common = min(len(expected), len(actual))
        for index in range(common):
            _compare_node(expected[index], actual[index], f"{path}[{index}]",
                          allow_extra_actual, ignored, differences)
        for index in range(common, len(expected)):
            differences.append(Difference(DifferenceType.MISSING, f"{path}[{index}]",
                                          _text(expected[index]), _MISSING))
        if not allow_extra_actual:
            for index in range(common, len(actual)):
                differences.append(Difference(DifferenceType.UNEXPECTED, f"{path}[{index}]",
                                              _MISSING, _text(actual[index])))
        return
    if expected != actual:
        differences.append(Difference(DifferenceType.VALUE, path, _text(expected), _text(actual)))


def _kind(value: Any) -> str:
    """与 Jackson NodeType 对齐的类型分类（bool 先于 int 判断）。"""
    if isinstance(value, dict):
        return "object"
    if isinstance(value, list):
        return "array"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if value is None:
        return "null"
    return "scalar"


def _is_ignored(path: str, ignored: set[str]) -> bool:
    if path in ignored:
        return True
    return any(_matches(path, pattern) for pattern in ignored)


def _matches(path: str, pattern: str) -> bool:
    if "*" not in pattern:
        return False
    parts = pattern.split("*")
    regex = r"[^.\[\]]+".join(re.escape(part) for part in parts)
    return re.fullmatch(regex, path) is not None


def _text(value: Any) -> str:
    return repr(value)
