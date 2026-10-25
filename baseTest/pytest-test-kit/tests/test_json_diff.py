"""json_diff 单元测试，对应 Java DataDriveUtilTest.comparesStrictAndLenientJson。"""

from pytest_testkit import DifferenceType, lenient, strict


class TestStrict:
    def test_identical_objects_have_no_difference(self):
        expected = {"code": 0, "data": ["shop-a"]}
        assert strict(expected, dict(expected)) == []

    def test_value_difference_is_reported_with_path(self):
        differences = strict({"code": 0}, {"code": 1})
        assert len(differences) == 1
        assert differences[0].type == DifferenceType.VALUE
        assert differences[0].path == "$.code"

    def test_missing_field_is_reported(self):
        differences = strict({"code": 0, "data": []}, {"code": 0})
        assert differences[0].type == DifferenceType.MISSING
        assert differences[0].path == "$.data"

    def test_unexpected_field_is_reported_in_strict_mode(self):
        differences = strict({"code": 0}, {"code": 0, "traceId": "abc"})
        assert differences[0].type == DifferenceType.UNEXPECTED
        assert differences[0].path == "$.traceId"

    def test_type_difference_is_reported(self):
        differences = strict({"data": ["a"]}, {"data": {"a": 1}})
        assert differences[0].type == DifferenceType.TYPE
        assert differences[0].expected == "array"
        assert differences[0].actual == "object"

    def test_bool_and_number_are_different_types(self):
        differences = strict({"flag": True}, {"flag": 1})
        assert differences[0].type == DifferenceType.TYPE

    def test_array_elements_are_compared_by_index(self):
        differences = strict({"data": [1, 2]}, {"data": [1, 3]})
        assert differences[0].path == "$.data[1]"

    def test_longer_actual_array_reports_unexpected_items(self):
        differences = strict({"data": [1]}, {"data": [1, 2]})
        assert differences[0].type == DifferenceType.UNEXPECTED
        assert differences[0].path == "$.data[1]"

    def test_shorter_actual_array_reports_missing_items(self):
        differences = strict({"data": [1, 2]}, {"data": [1]})
        assert differences[0].type == DifferenceType.MISSING
        assert differences[0].path == "$.data[1]"


class TestLenient:
    def test_extra_actual_fields_are_allowed(self):
        expected = {"code": 0}
        actual = {"code": 0, "traceId": "abc"}
        assert lenient(expected, actual) == []

    def test_ignored_path_skips_dynamic_field(self):
        expected = {"code": 0, "traceId": "expected"}
        actual = {"code": 0, "traceId": "dynamic-123"}
        assert lenient(expected, actual, "$.traceId") == []

    def test_wildcard_path_matches_every_child(self):
        expected = {"first": {"id": 1}, "second": {"id": 2}}
        actual = {"first": {"id": 9}, "second": {"id": 8}}
        assert strict(expected, actual, "$.*.id") == []

    def test_wildcard_does_not_cross_levels(self):
        expected = {"first": {"nested": {"id": 1}}}
        actual = {"first": {"nested": {"id": 9}}}
        assert strict(expected, actual, "$.*.id") != []
        assert strict(expected, actual, "$.first.*.id") == []

    def test_strict_and_lenient_agree_when_no_extra_fields(self):
        expected = {"code": 0, "data": ["a"]}
        assert strict(expected, {"code": 1, "data": ["a"]}) == \
            lenient(expected, {"code": 1, "data": ["a"]})


class TestDeepDiffParity:
    """与第三方库 deepdiff 的交叉验证：两者都应发现同样的差异。"""

    def test_deepdiff_detects_same_value_difference(self):
        from deepdiff import DeepDiff

        expected = {"code": 0, "data": ["shop-a"]}
        actual = {"code": 1, "data": ["shop-a"]}
        assert len(strict(expected, actual)) > 0
        assert DeepDiff(expected, actual) != {}
