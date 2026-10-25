"""运行时组合与结果发布，对应 Java 的 runtime 包。"""

import threading
from dataclasses import dataclass
from typing import Protocol

from pytest_testkit.identity import IdentityProvider, UserIdentity
from pytest_testkit.mock_engine import InMemoryMockEngine, MockEngine
from pytest_testkit.models import TestResultRecord, TestRunSummary


class ResultPublisher(Protocol):
    """结果发布契约，对应 Java 的 ResultPublisher SPI。"""

    def publish(self, result: TestResultRecord) -> None: ...

    def complete(self, summary: TestRunSummary) -> None: ...


class InMemoryResultPublisher:
    """本地内存结果收集器，对应 Java 的 InMemoryResultPublisher。"""

    def __init__(self) -> None:
        self._results: list[TestResultRecord] = []
        self._summary: TestRunSummary | None = None
        self._lock = threading.Lock()

    def publish(self, result: TestResultRecord) -> None:
        with self._lock:
            self._results.append(result)

    def complete(self, summary: TestRunSummary) -> None:
        self._summary = summary

    @property
    def results(self) -> list[TestResultRecord]:
        with self._lock:
            return list(self._results)

    @property
    def summary(self) -> TestRunSummary | None:
        return self._summary


class _UnsupportedIdentityProvider:
    def acquire(self, profile: str) -> UserIdentity:
        raise RuntimeError("No identity provider is configured")

    def release(self, identity: UserIdentity) -> None:
        pass


@dataclass(frozen=True)
class AutomationRuntime:
    """显式组合根，对应 Java 的 AutomationRuntime record。

    未提供的组件使用安全的离线默认值，框架不会访问网络。
    """

    identities: IdentityProvider | None = None
    mocks: MockEngine | None = None
    results: ResultPublisher | None = None

    def __post_init__(self) -> None:
        if self.identities is None:
            object.__setattr__(self, "identities", _UnsupportedIdentityProvider())
        if self.mocks is None:
            object.__setattr__(self, "mocks", InMemoryMockEngine())
        if self.results is None:
            object.__setattr__(self, "results", InMemoryResultPublisher())

    @classmethod
    def local(cls) -> "AutomationRuntime":
        return cls()


_registry_lock = threading.Lock()
_current_runtime = AutomationRuntime.local()


class RuntimeRegistry:
    """全局运行时注册表，对应 Java 的 RuntimeRegistry。"""

    @staticmethod
    def current() -> AutomationRuntime:
        return _current_runtime

    @staticmethod
    def install(runtime: AutomationRuntime) -> None:
        global _current_runtime
        if runtime is None:
            raise ValueError("runtime must not be None")
        with _registry_lock:
            _current_runtime = runtime

    @staticmethod
    def reset() -> None:
        global _current_runtime
        with _registry_lock:
            _current_runtime = AutomationRuntime.local()
