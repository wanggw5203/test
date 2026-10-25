"""身份上下文与账号池，对应 Java 的 identity 包。

- UserIdentity ↔ UserIdentity record（toString 不输出令牌）
- IdentityContext ↔ IdentityContext（ThreadLocal 栈 → ContextVar 栈，可嵌套）
- InMemoryIdentityProvider ↔ InMemoryIdentityProvider（并发租借不重复）
"""

import contextvars
import random
import threading
from collections.abc import Mapping
from dataclasses import dataclass, field
from typing import Protocol


@dataclass(frozen=True)
class UserIdentity:
    """测试账号。access_token 不会出现在字符串输出中。"""

    alias: str
    subject: str
    access_token: str
    attributes: Mapping[str, str] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.alias or not self.alias.strip():
            raise ValueError("alias must not be blank")

    def __str__(self) -> str:
        return (
            f"UserIdentity(alias={self.alias}, subject={self.subject}, "
            f"access_token=<redacted>)"
        )


_stack: contextvars.ContextVar[list[UserIdentity] | None] = contextvars.ContextVar(
    "kit-identity-stack", default=None)


def _get_stack() -> list[UserIdentity]:
    stack = _stack.get()
    if stack is None:
        stack = []
        _stack.set(stack)
    return stack


class IdentityContext:
    """可嵌套的身份作用域，用法：

    with IdentityContext.use(reviewer):
        assert IdentityContext.current() is reviewer
    """

    def __init__(self, identity: UserIdentity):
        if identity is None:
            raise ValueError("identity must not be null")
        self._closed = False
        _get_stack().append(identity)

    @classmethod
    def use(cls, identity: UserIdentity) -> "IdentityContext":
        return cls(identity)

    @classmethod
    def current(cls) -> UserIdentity | None:
        stack = _stack.get()
        return stack[-1] if stack else None

    @classmethod
    def require_current(cls) -> UserIdentity:
        identity = cls.current()
        if identity is None:
            raise LookupError("No identity is active")
        return identity

    @classmethod
    def clear(cls) -> None:
        _stack.set(None)

    def close(self) -> None:
        if self._closed:
            return
        stack = _get_stack()
        if stack:
            stack.pop()
        self._closed = True

    def __enter__(self) -> "IdentityContext":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()


class IdentityProvider(Protocol):
    """账号池契约，对应 Java 的 IdentityProvider SPI。"""

    def acquire(self, profile: str) -> UserIdentity: ...

    def release(self, identity: UserIdentity) -> None: ...


class InMemoryIdentityProvider:
    """本地账号池：同一账号同一时刻只能被一个用例租用。"""

    def __init__(self, profiles: Mapping[str, list[UserIdentity]] | None = None):
        self._profiles = {name: list(accounts) for name, accounts in (profiles or {}).items()}
        self._leased: set[str] = set()
        self._lock = threading.Lock()

    def acquire(self, profile: str) -> UserIdentity:
        candidates = list(self._profiles.get(profile, []))
        random.shuffle(candidates)
        with self._lock:
            for candidate in candidates:
                if candidate.alias not in self._leased:
                    self._leased.add(candidate.alias)
                    return candidate
        raise RuntimeError(f"No identity is available for profile: {profile}")

    def release(self, identity: UserIdentity) -> None:
        if identity is None:
            return
        with self._lock:
            self._leased.discard(identity.alias)
