"""Mock 引擎测试，对应 Java InMemoryMockEngineTest。"""

import pytest

from pytest_testkit import InMemoryMockEngine, MockRule, MockScenario, MockScope


class TestMockModels:
    def test_scenario_defaults_to_case_scope(self):
        scenario = MockScenario(id="inventory-ok")
        assert scenario.scope == MockScope.CASE
        assert scenario.rules == ()

    def test_scenario_requires_id(self):
        with pytest.raises(ValueError, match="id"):
            MockScenario(id="")

    def test_rule_requires_protocol_and_target(self):
        with pytest.raises(ValueError, match="protocol"):
            MockRule(protocol="", target="/api")
        with pytest.raises(ValueError, match="target"):
            MockRule(protocol="http", target="")

    def test_rule_times_defaults_to_one_when_invalid(self):
        rule = MockRule(protocol="http", target="/api", times=0)
        assert rule.times == 1


class TestInMemoryMockEngine:
    def test_open_registers_active_scenario(self):
        engine = InMemoryMockEngine()
        scenario = MockScenario(id="inventory-ok")
        session = engine.open(scenario)
        assert session.active()
        assert engine.active_scenarios()[session.id] == scenario

    def test_close_removes_scenario(self):
        engine = InMemoryMockEngine()
        session = engine.open(MockScenario(id="inventory-ok"))
        session.close()
        assert not session.active()
        assert engine.active_scenarios() == {}

    def test_sessions_are_independent(self):
        engine = InMemoryMockEngine()
        first = engine.open(MockScenario(id="case-a"))
        second = engine.open(MockScenario(id="case-b"))
        first.close()
        assert second.active()
        assert list(engine.active_scenarios()) == [second.id]

    def test_close_is_idempotent(self):
        engine = InMemoryMockEngine()
        session = engine.open(MockScenario(id="case-a"))
        session.close()
        session.close()
        assert engine.active_scenarios() == {}
