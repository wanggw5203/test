package io.testkit.basetest.identity;

public interface IdentityProvider {
    UserIdentity acquire(String profile);

    default void release(UserIdentity identity) {
    }
}
