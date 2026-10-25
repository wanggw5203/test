"""通用模型，对应 Java 版的 record/POJO。"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum


@dataclass(frozen=True)
class CaseContext:
    """一条数据驱动用例的上下文，对应 Java 的 CaseContext record。"""

    name: str
    thought: str | None = None
    mock_id: str | None = None
    users: tuple[str, ...] = ()
    extra_params: dict = field(default_factory=dict)

    @classmethod
    def from_case(cls, case: "TestCaseData") -> "CaseContext":
        return cls(
            name=case.name,
            thought=case.thought,
            mock_id=case.mock_id,
            users=tuple(case.users),
            extra_params=dict(case.extra_params),
        )


@dataclass
class TestCaseData:
    """YAML 套件中的单条用例，字段与 Java 的 TestCaseData 一致。"""

    name: str
    enabled: bool = True
    thought: str | None = None
    mock_id: str | None = None
    users: list[str] = field(default_factory=list)
    request: list[str] = field(default_factory=list)
    expect: list[str] = field(default_factory=list)
    extra_params: dict = field(default_factory=dict)


@dataclass
class TestDataSuite:
    """YAML 套件根节点，字段与 Java 的 TestDataSuite 一致。"""

    description: str = ""
    cases: list[TestCaseData] = field(default_factory=list)
    debug_cases: list[str] = field(default_factory=list)


class DifferenceType(Enum):
    MISSING = "MISSING"
    UNEXPECTED = "UNEXPECTED"
    VALUE = "VALUE"
    TYPE = "TYPE"


@dataclass(frozen=True)
class Difference:
    """JSON 比较差异，输出格式与 Java 的 Difference.toString 保持一致。"""

    type: DifferenceType
    path: str
    expected: str
    actual: str

    def __str__(self) -> str:
        return (
            f"{self.type.value} at {self.path}: "
            f"expected={self.expected}, actual={self.actual}"
        )


class Status(Enum):
    PASSED = "PASSED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


@dataclass(frozen=True)
class TestResultRecord:
    """单条用例结果，对应 Java 的 TestResultRecord。"""

    run_id: str
    case_id: str
    name: str
    status: Status
    started_at: datetime
    duration_ms: int
    error: str | None = None
    metadata: dict = field(default_factory=dict)


@dataclass(frozen=True)
class TestRunSummary:
    """整次运行汇总，对应 Java 的 TestRunSummary。"""

    run_id: str
    started_at: datetime
    finished_at: datetime
    passed: int
    failed: int
    skipped: int
