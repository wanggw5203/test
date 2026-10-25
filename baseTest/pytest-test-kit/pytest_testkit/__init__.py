"""pytest-test-kit：Python/pytest 版通用测试基础库。

与 Java 版 base-test-toolkit 能力对照：
- data_drive  ↔ DataDriveUtil（YAML 数据驱动）
- json_diff   ↔ JsonDiff（严格/宽松 JSON 比较）
- identity    ↔ IdentityContext + InMemoryIdentityProvider
- mock_engine ↔ MockScenario + InMemoryMockEngine
- runtime     ↔ AutomationRuntime + ResultPublisher
- context     ↔ CaseContextHolder + ReturnValueContext
"""

from pytest_testkit.context import CaseContextHolder, ReturnValueContext
from pytest_testkit.data_drive import CaseArguments, load_test_data
from pytest_testkit.identity import (
    IdentityContext,
    IdentityProvider,
    InMemoryIdentityProvider,
    UserIdentity,
)
from pytest_testkit.json_diff import Difference, DifferenceType, lenient, strict
from pytest_testkit.mock_engine import (
    InMemoryMockEngine,
    MockEngine,
    MockRule,
    MockScenario,
    MockScope,
    MockSession,
)
from pytest_testkit.models import (
    CaseContext,
    Status,
    TestCaseData,
    TestDataSuite,
    TestResultRecord,
    TestRunSummary,
)
from pytest_testkit.runtime import (
    AutomationRuntime,
    InMemoryResultPublisher,
    ResultPublisher,
    RuntimeRegistry,
)

__version__ = "1.0.0"

__all__ = [
    "AutomationRuntime",
    "CaseArguments",
    "CaseContext",
    "CaseContextHolder",
    "Difference",
    "DifferenceType",
    "IdentityContext",
    "IdentityProvider",
    "InMemoryIdentityProvider",
    "InMemoryMockEngine",
    "InMemoryResultPublisher",
    "MockEngine",
    "MockRule",
    "MockScenario",
    "MockScope",
    "MockSession",
    "ResultPublisher",
    "ReturnValueContext",
    "RuntimeRegistry",
    "Status",
    "TestCaseData",
    "TestDataSuite",
    "TestResultRecord",
    "TestRunSummary",
    "UserIdentity",
    "lenient",
    "load_test_data",
    "strict",
]
