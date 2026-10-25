"""数据驱动加载测试，对应 Java DataDriveUtilTest.loadsYamlAndJson5Payloads。"""

from pathlib import Path

import pytest

from pytest_testkit import load_test_data

SUITE_FILE = Path(__file__).parent / "cases" / "order.yaml"


class TestLoadTestData:
    def test_disabled_cases_are_filtered_out(self):
        rows = load_test_data(SUITE_FILE)
        assert len(rows) == 2

    def test_case_context_is_built_from_yaml(self):
        rows = load_test_data(SUITE_FILE)
        first = rows[0].context
        assert first.name == "apply coupon discount"
        assert first.mock_id == "inventory-ok"
        assert first.users == ("reviewer",)
        assert first.extra_params == {"channel": "web"}

    def test_payloads_are_loaded_in_request_then_expect_order(self):
        rows = load_test_data(SUITE_FILE)
        request, expected = rows[0].payloads
        assert request == {"productId": "P-100", "quantity": 2, "couponCode": "SAVE20"}
        assert expected["totalPrice"] == 180

    def test_missing_suite_file_raises(self):
        with pytest.raises(FileNotFoundError):
            load_test_data(Path(__file__).parent / "cases" / "missing.yaml")

    def test_document_without_root_node_raises(self, tmp_path):
        broken = tmp_path / "broken.yaml"
        broken.write_text("otherRoot:\n  cases: []\n", encoding="utf-8")
        with pytest.raises(ValueError, match="testDataSuite"):
            load_test_data(broken)

    def test_debug_cases_filter_only_runs_named_cases(self, tmp_path):
        suite = tmp_path / "suite.yaml"
        suite.write_text(
            "testDataSuite:\n"
            "  debugCases: [second]\n"
            "  cases:\n"
            "    - name: first\n"
            "    - name: second\n",
            encoding="utf-8",
        )
        rows = load_test_data(suite)
        assert [row.context.name for row in rows] == ["second"]
