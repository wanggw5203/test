"""Mock 场景与生命周期模型，对应 Java 的 mock 包。

InMemoryMockEngine 与 Java 版一样只记录场景、不拦截网络，
用于学习和单元测试；接入真实网络拦截时实现 MockEngine
（Python 侧可配合 responses / WireMock 使用）。
"""

import threading
import uuid
from collections.abc import Mapping
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Protocol


class MockScope(Enum):
    CASE = "CASE"
    THREAD = "THREAD"
    SUITE = "SUITE"


@dataclass(frozen=True)
class MockRule:
    protocol: str
    target: str
    request: Mapping[str, Any] = field(default_factory=dict)
    response: Any = None
    times: int = 1

    def __post_init__(self) -> None:
        if not self.protocol or not self.protocol.strip():
            raise ValueError("protocol must not be blank")
        if not self.target or not self.target.strip():
            raise ValueError("target must not be blank")
        if self.times <= 0:
            object.__setattr__(self, "times", 1)


@dataclass(frozen=True)
class MockScenario:
    id: str
    scope: MockScope = MockScope.CASE
    rules: tuple[MockRule, ...] = ()

    def __post_init__(self) -> None:
        if not self.id or not self.id.strip():
            raise ValueError("id must not be blank")
        object.__setattr__(self, "rules", tuple(self.rules))


class MockSession(Protocol):
    id: str
    scenario: MockScenario

    def active(self) -> bool: ...

    def close(self) -> None: ...


class MockEngine(Protocol):
    """Mock 引擎契约，对应 Java 的 MockEngine SPI。"""

    def open(self, scenario: MockScenario) -> MockSession: ...


@dataclass
class _Session:
    id: str
    scenario: MockScenario
    _engine: "InMemoryMockEngine" = field(repr=False)

    def active(self) -> bool:
        return self.id in self._engine._active

    def close(self) -> None:
        self._engine._active.pop(self.id, None)


class InMemoryMockEngine:
    """本地内存实现：记录活跃场景，供断言和清理验证。"""

    def __init__(self) -> None:
        self._active: dict[str, MockScenario] = {}
        self._lock = threading.Lock()

    def open(self, scenario: MockScenario) -> MockSession:
        session_id = str(uuid.uuid4())
        with self._lock:
            self._active[session_id] = scenario
        return _Session(session_id, scenario, self)

    def active_scenarios(self) -> dict[str, MockScenario]:
        with self._lock:
            return dict(self._active)
