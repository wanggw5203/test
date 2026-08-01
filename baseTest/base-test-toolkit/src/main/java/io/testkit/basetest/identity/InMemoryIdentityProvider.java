package io.testkit.basetest.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Local account pool that prevents the same identity from being used concurrently. */
public final class InMemoryIdentityProvider implements IdentityProvider {
    private final Map<String, List<UserIdentity>> profiles;
    private final ConcurrentHashMap<String, Boolean> leased = new ConcurrentHashMap<>();

    public InMemoryIdentityProvider(Map<String, ? extends Collection<UserIdentity>> profiles) {
        Map<String, List<UserIdentity>> copy = new LinkedHashMap<>();
        if (profiles != null) {
            profiles.forEach((key, values) -> copy.put(key, List.copyOf(values)));
        }
        this.profiles = Map.copyOf(copy);
    }

    @Override
    public UserIdentity acquire(String profile) {
        List<UserIdentity> candidates = new ArrayList<>(profiles.getOrDefault(profile, List.of()));
        Collections.shuffle(candidates);
        for (UserIdentity candidate : candidates) {
            if (leased.putIfAbsent(candidate.alias(), Boolean.TRUE) == null) return candidate;
        }
        throw new IllegalStateException("No identity is available for profile: " + profile);
    }

    @Override
    public void release(UserIdentity identity) {
        if (identity != null) leased.remove(identity.alias());
    }
}
