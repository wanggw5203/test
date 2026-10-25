"""运行时组合测试，对应 Java AutomationRuntimeTest。"""

import pytest

from pytest_testkit import (
    AutomationRuntime,
    InMemoryMockEngine,
    InMemoryResultPublisher,
    RuntimeRegistry,
)


class TestAutomationRuntime:
    def test_local_runtime_provides_safe_offline_defaults(self):
        runtime = AutomationRuntime.local()
        assert isinstance(runtime.mocks, InMemoryMockEngine)
        assert isinstance(runtime.results, InMemoryResultPublisher)

    def test_default_identity_provider_refuses_acquire(self):
        runtime = AutomationRuntime.local()
        with pytest.raises(RuntimeError, match="No identity provider"):
            runtime.identities.acquire("reviewer")

    def test_explicit_components_are_kept(self):
        publisher = InMemoryResultPublisher()
        runtime = AutomationRuntime(results=publisher)
        assert runtime.results is publisher


class TestRuntimeRegistry:
    def teardown_method(self):
        RuntimeRegistry.reset()

    def test_install_and_current(self):
        runtime = AutomationRuntime.local()
        RuntimeRegistry.install(runtime)
        assert RuntimeRegistry.current() is runtime

    def test_reset_restores_local_runtime(self):
        before = RuntimeRegistry.current()
        RuntimeRegistry.install(AutomationRuntime.local())
        RuntimeRegistry.reset()
        assert RuntimeRegistry.current() is not before
