"""身份上下文测试，对应 Java IdentityContextTest。"""

import threading

import pytest

from pytest_testkit import IdentityContext, InMemoryIdentityProvider, UserIdentity


def make_identity(alias):
    return UserIdentity(alias=alias, subject=f"u-{alias}", access_token=f"token-{alias}")


class TestIdentityContext:
    def teardown_method(self):
        IdentityContext.clear()

    def test_use_and_current(self):
        reviewer = make_identity("reviewer-1")
        with IdentityContext.use(reviewer):
            assert IdentityContext.current() is reviewer
            assert IdentityContext.require_current() is reviewer
        assert IdentityContext.current() is None

    def test_nested_context_restores_outer_identity(self):
        outer = make_identity("reviewer-1")
        inner = make_identity("operator-1")
        with IdentityContext.use(outer):
            with IdentityContext.use(inner):
                assert IdentityContext.current() is inner
            assert IdentityContext.current() is outer
        assert IdentityContext.current() is None

    def test_require_current_raises_without_identity(self):
        with pytest.raises(LookupError):
            IdentityContext.require_current()

    def test_contexts_are_isolated_between_threads(self):
        main_identity = make_identity("reviewer-1")
        observed_in_thread = {}

        def worker():
            observed_in_thread["before"] = IdentityContext.current()
            with IdentityContext.use(make_identity("operator-1")):
                observed_in_thread["inside"] = IdentityContext.current().alias

        with IdentityContext.use(main_identity):
            thread = threading.Thread(target=worker)
            thread.start()
            thread.join()
            assert observed_in_thread["before"] is None
            assert observed_in_thread["inside"] == "operator-1"
            assert IdentityContext.current() is main_identity

    def test_token_is_not_in_string_output(self):
        identity = make_identity("reviewer-1")
        assert "token-reviewer-1" not in str(identity)
        assert "<redacted>" in str(identity)


class TestInMemoryIdentityProvider:
    def test_acquire_and_release(self):
        provider = InMemoryIdentityProvider({"reviewer": [make_identity("r1")]})
        identity = provider.acquire("reviewer")
        assert identity.alias == "r1"
        provider.release(identity)
        assert provider.acquire("reviewer").alias == "r1"

    def test_same_identity_cannot_be_leased_twice(self):
        provider = InMemoryIdentityProvider({"reviewer": [make_identity("r1")]})
        provider.acquire("reviewer")
        with pytest.raises(RuntimeError, match="No identity is available"):
            provider.acquire("reviewer")

    def test_pool_falls_back_to_second_account(self):
        provider = InMemoryIdentityProvider(
            {"reviewer": [make_identity("r1"), make_identity("r2")]})
        first = provider.acquire("reviewer")
        second = provider.acquire("reviewer")
        assert {first.alias, second.alias} == {"r1", "r2"}

    def test_concurrent_acquire_never_returns_duplicates(self):
        provider = InMemoryIdentityProvider(
            {"reviewer": [make_identity(f"r{i}") for i in range(5)]})
        acquired = []
        lock = threading.Lock()

        def worker():
            try:
                identity = provider.acquire("reviewer")
                with lock:
                    acquired.append(identity.alias)
            except RuntimeError:
                pass

        threads = [threading.Thread(target=worker) for _ in range(8)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        assert len(acquired) == len(set(acquired)) == 5
