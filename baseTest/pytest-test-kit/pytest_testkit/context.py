"""线程/协程隔离的上下文，对应 Java 的 CaseContextHolder 和 ReturnValueContext。

Java 用 ThreadLocal 实现隔离，Python 用 contextvars：
- 每个线程有独立的上下文，天然线程隔离；
- 每个 asyncio 任务在复制的上下文中运行，协程间也互不干扰。
"""

import contextvars
from typing import Any

from pytest_testkit.models import CaseContext

_current_case: contextvars.ContextVar[CaseContext | None] = contextvars.ContextVar(
    "kit-current-case", default=None)
_return_values: contextvars.ContextVar[dict[str, Any] | None] = contextvars.ContextVar(
    "kit-return-values", default=None)


class CaseContextHolder:
    """当前用例上下文，由生命周期钩子绑定（对应 Java BaseTest 的 @BeforeMethod）。"""

    @staticmethod
    def set(context: CaseContext) -> None:
        _current_case.set(context)

    @staticmethod
    def current() -> CaseContext | None:
        return _current_case.get()

    @staticmethod
    def require_current() -> CaseContext:
        context = _current_case.get()
        if context is None:
            raise LookupError("No data-driven case is active")
        return context

    @staticmethod
    def clear() -> None:
        _current_case.set(None)


class ReturnValueContext:
    """跨步骤返回值存储：数据准备阶段写入，测试调用阶段读取。"""

    @staticmethod
    def put(name: str, value: Any) -> None:
        if not name or not name.strip():
            raise ValueError("name must not be blank")
        values = _return_values.get()
        if values is None:
            values = {}
            _return_values.set(values)
        values[name] = value

    @staticmethod
    def get(name: str) -> Any | None:
        values = _return_values.get()
        return None if values is None else values.get(name)

    @staticmethod
    def snapshot() -> dict[str, Any]:
        values = _return_values.get()
        return dict(values) if values else {}

    @staticmethod
    def clear() -> None:
        _return_values.set(None)
