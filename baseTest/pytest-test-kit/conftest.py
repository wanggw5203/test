"""pytest 生命周期钩子，对应 Java 的 PortableTestNgListener。

职责与 Java 监听器一一对应：
- @pytest.mark.use_identity("profile") ↔ @UseIdentity：租借/归还账号
- 用例的 mockId ↔ CaseContext.mockId：打开/关闭 Mock 场景
- CaseContext 绑定到 CaseContextHolder ↔ BaseTest 的 @BeforeMethod
- 每条用例发布 TestResultRecord ↔ onTestSuccess/Failure/Skipped
- 会话结束发布 TestRunSummary ↔ onExecutionFinish

清理放在 fixture 的 yield 之后，等价于 Java 监听器的 finally 块：
即使断言失败，身份、Mock 场景和上下文也会被释放。
"""

import time
import uuid
from datetime import datetime, timezone

import pytest

from pytest_testkit.context import CaseContextHolder, ReturnValueContext
from pytest_testkit.data_drive import CaseArguments
from pytest_testkit.identity import IdentityContext
from pytest_testkit.mock_engine import MockScenario
from pytest_testkit.models import Status, TestResultRecord, TestRunSummary
from pytest_testkit.runtime import RuntimeRegistry

_run_state: dict = {}


def pytest_configure(config):
    config.addinivalue_line(
        "markers",
        "use_identity(profile): 从运行时账号池为该用例租借一个身份，用例结束自动归还",
    )


def pytest_sessionstart(session):
    _run_state.update(
        run_id=str(uuid.uuid4()),
        started_at=datetime.now(timezone.utc),
        passed=0,
        failed=0,
        skipped=0,
    )


def _case_of(item) -> CaseArguments | None:
    callspec = getattr(item, "callspec", None)
    if callspec is None:
        return None
    for value in callspec.params.values():
        if isinstance(value, CaseArguments):
            return value
    return None


@pytest.fixture(autouse=True)
def kit_lifecycle(request):
    """身份租借、Mock 场景、用例上下文绑定与清理。"""
    item = request.node
    runtime = RuntimeRegistry.current()
    identity = None
    scope = None
    mock_session = None

    marker = item.get_closest_marker("use_identity")
    if marker is not None:
        identity = runtime.identities.acquire(marker.args[0])
        scope = IdentityContext.use(identity)

    case = _case_of(item)
    if case is not None:
        CaseContextHolder.set(case.context)
        if case.context.mock_id:
            mock_session = runtime.mocks.open(MockScenario(id=case.context.mock_id))

    yield

    if mock_session is not None:
        mock_session.close()
    if scope is not None:
        scope.close()
    if identity is not None:
        runtime.identities.release(identity)
    CaseContextHolder.clear()
    ReturnValueContext.clear()


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    reports = getattr(item, "_kit_reports", {})
    reports[report.when] = report
    item._kit_reports = reports


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_protocol(item, nextitem):
    started_at = datetime.now(timezone.utc)
    start = time.perf_counter()
    yield
    duration_ms = max(0, int((time.perf_counter() - start) * 1000))

    reports = getattr(item, "_kit_reports", {})
    setup = reports.get("setup")
    call = reports.get("call")
    if setup is not None and setup.skipped:
        status, error = Status.SKIPPED, None
    elif setup is not None and setup.failed:
        status, error = Status.FAILED, str(setup.longrepr)
    elif call is None or call.skipped:
        status, error = Status.SKIPPED, None
    elif call.failed:
        status, error = Status.FAILED, str(call.longrepr)
    else:
        status, error = Status.PASSED, None

    case = _case_of(item)
    name = case.context.name if case else item.name
    case_id = case.context.name if case else item.nodeid
    record = TestResultRecord(
        run_id=_run_state["run_id"],
        case_id=case_id,
        name=name,
        status=status,
        started_at=started_at,
        duration_ms=duration_ms,
        error=error,
        metadata={"nodeid": item.nodeid},
    )
    RuntimeRegistry.current().results.publish(record)
    counters = {Status.PASSED: "passed", Status.FAILED: "failed", Status.SKIPPED: "skipped"}
    _run_state[counters[status]] += 1


def pytest_sessionfinish(session, exitstatus):
    RuntimeRegistry.current().results.complete(TestRunSummary(
        run_id=_run_state["run_id"],
        started_at=_run_state["started_at"],
        finished_at=datetime.now(timezone.utc),
        passed=_run_state["passed"],
        failed=_run_state["failed"],
        skipped=_run_state["skipped"],
    ))
